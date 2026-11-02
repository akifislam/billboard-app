import Foundation

@MainActor final class UtilityService {
    static let shared = UtilityService()
    private let standard = URLSession.shared
    private let insecure = URLSession(configuration: .default, delegate: InsecureSessionDelegate(), delegateQueue: nil)
    private let agent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"

    func fetch(_ meter: SavedMeter) async throws -> MeterSnapshot {
        guard meter.number.range(of: "^[A-Za-z0-9-]{4,30}$", options: .regularExpression) != nil else { throw BillBoardError.invalidIdentifier }
        switch (meter.provider, meter.billingType) {
        case (.nesco, .prepaid): return try await nescoPrepaid(meter.number)
        case (.nesco, .postpaid): return try await nescoPostpaid(meter.number)
        case (.desco, _): return try await desco(meter.number)
        case (.pgcl, _): return try await pgcl(meter.number)
        }
    }

    private func request(_ url: URL, method: String = "GET", body: Data? = nil, headers: [String: String] = [:], insecure: Bool = false) async throws -> (Data, HTTPURLResponse) {
        var request = URLRequest(url: url, timeoutInterval: 30)
        request.httpMethod = method; request.httpBody = body
        request.setValue(agent, forHTTPHeaderField: "User-Agent")
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        let (data, response) = try await (insecure ? self.insecure : standard).data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw BillBoardError.badResponse("The utility portal returned an error. Please try again.")
        }
        return (data, http)
    }

    private func postNesco(path: String, number: String, submit: String) async throws -> String {
        let url = URL(string: "https://customer.nesco.gov.bd/\(path)")!
        let (getData, response) = try await request(url)
        let page = String(decoding: getData, as: UTF8.self)
        guard let token = page.firstMatch(#"name=[\"'](?:csrf-token|_token)[\"'][^>]*(?:content|value)=[\"']([^\"']+)"#) else { throw BillBoardError.parsing("NESCO security token was not found.") }
        let cookies = response.value(forHTTPHeaderField: "Set-Cookie")?.split(separator: ",").map { $0.split(separator: ";")[0] }.joined(separator: "; ") ?? ""
        let form = ["_token": token, "cust_no": number, "submit": submit].map { "\($0.key.urlEncoded)=\($0.value.urlEncoded)" }.joined(separator: "&").data(using: .utf8)
        let headers = ["Content-Type":"application/x-www-form-urlencoded", "Origin":"https://customer.nesco.gov.bd", "Referer":url.absoluteString, "Cookie":cookies]
        let (data, _) = try await request(url, method: "POST", body: form, headers: headers)
        return String(decoding: data, as: UTF8.self)
    }

    private func nescoPrepaid(_ number: String) async throws -> MeterSnapshot {
        async let rechargePage = postNesco(path: "pre/panel", number: number, submit: "রিচার্জ হিস্ট্রি")
        async let usagePage = postNesco(path: "pre/panel", number: number, submit: "মাসিক ব্যবহার")
        let (html, monthlyHTML) = try await (rechargePage, usagePage)
        let fields = html.labelInputFields(), balance = fields.value(prefix: "অবশিষ্ট ব্যালেন্স")?.number
        guard balance != nil || fields.value(prefix: "গ্রাহকের নাম") != nil || fields.value(prefix: "মিটার নম্বর") != nil else { throw BillBoardError.notFound("No NESCO prepaid meter was found with this number.") }
        let detailMap = [("Customer Name","গ্রাহকের নাম"),("Father/Husband's Name","পিতা/স্বামীর নাম"),("Address","ঠিকানা"),("Mobile","মোবাইল"),("Office","সংশ্লিষ্ট বিদ্যুৎ অফিস"),("Feeder","ফিডারের নাম"),("Consumer Number","কনজ্যুমার নম্বর"),("Meter Number","মিটার নম্বর"),("Approved Load","অনুমোদিত লোড"),("Tariff","অনুমোদিত ট্যারিফ"),("Meter Type","মিটারের ধরণ"),("Meter Status","মিটার স্ট্যাটাস"),("Install Date","মিটার স্থাপনের তারিখ"),("Minimum Recharge","মিনিমাম রিচার্জের পরিমাণ")]
        let details = detailMap.compactMap { name, key in fields.value(prefix: key).map { Field(label: name, value: $0) } }
        let recharge = html.tableRows(matching: "টোকেন").first.map { c in HistoryRow(title: c[safe:13] ?? "Last Recharge", fields:[Field(label:"Token Number",value:c[safe:2] ?? "—"),Field(label:"Meter Rent",value:c[safe:3] ?? "—"),Field(label:"Demand Charge",value:c[safe:4] ?? "—"),Field(label:"VAT",value:c[safe:6] ?? "—"),Field(label:"Electricity",value:c[safe:9] ?? "—"),Field(label:"Recharge Amount",value:c[safe:10] ?? "—"),Field(label:"Possible Units",value:c[safe:11] ?? "—"),Field(label:"Method",value:c[safe:12] ?? "—"),Field(label:"Status",value:c[safe:14] ?? "—")]) }
        let monthly = monthlyHTML.tableRows(matching: "সর্বমোট রিচার্জ").map { c in HistoryRow(title:"\(c[safe:1] ?? "") \(c[safe:0] ?? "")",fields:[Field(label:"Total Recharge",value:c[safe:2] ?? "—"),Field(label:"Electricity Used",value:c[safe:4] ?? "—"),Field(label:"Total Usage",value:c[safe:10] ?? "—"),Field(label:"End Balance",value:c[safe:11] ?? "—"),Field(label:"Units Used (kWh)",value:c[safe:12] ?? "—")]) }
        let usage = monthly.prefix(3).compactMap { $0.fields.first(where:{$0.label == "Total Usage"})?.value.number }.filter{$0 > 0}
        let prediction = balance.flatMap { value in usage.isEmpty ? nil : value / (usage.reduce(0,+) / Double(usage.count) / 30.44) }
        return MeterSnapshot(balance: balance, balanceAsOf: html.firstMatch(#"(\d{1,2}\s+[A-Za-z]+\s+\d{4}[^<)]*?[AP]M)"#), customerName: fields.value(prefix:"গ্রাহকের নাম"), lastPaymentAmount: recharge?.fields.first(where:{$0.label == "Recharge Amount"})?.value.number, lastPaymentDate: recharge?.title, predictionDays: prediction, details: details, primaryHistoryTitle:"Last Recharge", primaryHistory:recharge.map{[$0]} ?? [], secondaryHistoryTitle:"মাসিক ব্যবহার (Monthly Usage)", secondaryHistory:monthly)
    }

    private func nescoPostpaid(_ number: String) async throws -> MeterSnapshot {
        let html = try await postNesco(path:"post/bill", number:number, submit:"সকল বিল")
        guard html.contains("গ্রাহকের নাম") else { throw BillBoardError.notFound("NESCO postpaid meter/account number not found.") }
        let fields = html.labelInputFields()
        let bills = html.tableRows(matching:"পেমেন্ট স্ট্যাটাস").map { c in HistoryRow(title:"\(c[safe:2] ?? "Bill") \(c[safe:1] ?? "")",fields:[Field(label:"Bill Number",value:c[safe:0] ?? "—"),Field(label:"Total Bill",value:c[safe:3] ?? "—"),Field(label:"Late Fee",value:c[safe:4] ?? "—"),Field(label:"Due Date",value:c[safe:5] ?? "—"),Field(label:"Paid Amount",value:c[safe:6] ?? "—"),Field(label:"Paid Date",value:c[safe:7] ?? "—"),Field(label:"Payment Status",value:c[safe:9] ?? "—")]) }
        let detailMap = [("Customer Name","গ্রাহকের নাম"),("Father/Husband's Name","পিতা/স্বামীর নাম"),("Address","ঠিকানা"),("Mobile","মোবাইল"),("Office","সংশ্লিষ্ট বিদ্যুৎ অফিস"),("Feeder","ফিডারের নাম"),("Consumer Number","কনজ্যুমার নম্বর"),("Meter Number","মিটার নম্বর"),("Approved Load","অনুমোদিত লোড"),("Tariff","অনুমোদিত ট্যারিফ"),("Meter Status","মিটার স্ট্যাটাস")]
        let details = detailMap.compactMap { label,key in fields.value(prefix:key).map{Field(label:label,value:$0)} }
        let unpaid = bills.filter { !($0.fields.first(where:{$0.label == "Payment Status"})?.value.lowercased().contains("paid") ?? false) }
        let due = unpaid.compactMap{$0.fields.first(where:{$0.label == "Total Bill"})?.value.number}.reduce(0,+)
        return MeterSnapshot(summary:unpaid.isEmpty ? "No due bills" : "Due: \(due.taka)", isDue:!unpaid.isEmpty, customerName:fields.value(prefix:"গ্রাহকের নাম"), details:details, primaryHistoryTitle:"All Bills", primaryHistory:bills)
    }

    private func desco(_ number: String) async throws -> MeterSnapshot {
        let base = "https://prepaid.desco.org.bd/api/unified/customer"
        var chosen: String?, balanceObject: [String:Any]?
        for key in ["meterNo","accountNo"] { if let value = try? await jsonData("\(base)/getBalance", query:[key:number], insecure:true) as? [String:Any] { chosen=key; balanceObject=value; break } }
        guard let key=chosen, let balanceObject else { throw BillBoardError.notFound("DESCO account/meter number not found.") }
        let info = (try? await jsonData("\(base)/getCustomerInfo",query:[key:number],insecure:true)) as? [String:Any] ?? [:]
        let calendar=Calendar.current, now=Date(), yearAgo=calendar.date(byAdding:.year,value:-1,to:now)!, monthsAgo=calendar.date(byAdding:.month,value:-11,to:now)!
        let formatter=DateFormatter(); formatter.dateFormat="yyyy-MM-dd"
        let rechargeAny = try? await jsonData("\(base)/getRechargeHistory",query:[key:number,"dateFrom":formatter.string(from:yearAgo),"dateTo":formatter.string(from:now)],insecure:true)
        let recharges=rechargeAny as? [[String:Any]] ?? []; formatter.dateFormat="yyyy-MM"
        let consumptionAny = try? await jsonData("\(base)/getCustomerMonthlyConsumption",query:[key:number,"monthFrom":formatter.string(from:monthsAgo),"monthTo":formatter.string(from:now)],insecure:true)
        let consumption=consumptionAny as? [[String:Any]] ?? []
        func str(_ object:[String:Any],_ key:String)->String? { object[key].map{String(describing:$0)}.flatMap{$0 == "<null>" ? nil:$0} }
        let balance=str(balanceObject,"balance")?.number
        let details:[Field]=[("Name","customerName"),("Account No","accountNo"),("Meter No","meterNo"),("Contact No","contactNo"),("Tariff Solution","tariffSolution"),("S & D","SDName"),("Transformer","transformer"),("Feeder","feederName"),("Installation Date","installationDate"),("Meter Model","meterModel"),("Phase Type","phaseType"),("Sanctioned Load","sanctionLoad"),("Installation Address","installationAddress")].compactMap{label,field in (str(info,field) ?? str(balanceObject,field)).map{Field(label:label,value:$0)}}
        let rechargeRows=recharges.sorted{(str($0,"rechargeDate") ?? "") > (str($1,"rechargeDate") ?? "")}.map{r in HistoryRow(title:str(r,"rechargeDate") ?? "Recharge",fields:[Field(label:"Recharge Amount",value:str(r,"totalAmount") ?? "—"),Field(label:"VAT",value:str(r,"VAT") ?? str(r,"vat") ?? "—"),Field(label:"Energy Amount",value:str(r,"energyAmount") ?? "—")])}
        let sortedConsumption=consumption.sorted{(str($0,"month") ?? "") > (str($1,"month") ?? "")}
        let monthlyRows=sortedConsumption.map{r in HistoryRow(title:str(r,"month") ?? "Month",fields:[Field(label:"Consumed (BDT)",value:str(r,"consumedTaka") ?? "—"),Field(label:"Consumed (kWh)",value:str(r,"consumedUnit") ?? "—"),Field(label:"Maximum Demand",value:str(r,"maximumDemand") ?? "—")])}
        let recent=sortedConsumption.prefix(3).compactMap{str($0,"consumedTaka")?.number}.filter{$0 > 0}; let prediction=balance.flatMap{recent.isEmpty ? nil:$0/(recent.reduce(0,+)/Double(recent.count)/30.44)}
        return MeterSnapshot(balance:balance,balanceAsOf:str(balanceObject,"readingTime"),customerName:str(info,"customerName"),lastPaymentAmount:rechargeRows.first?.fields.first?.value.number,lastPaymentDate:rechargeRows.first?.title,predictionDays:prediction,details:details,primaryHistoryTitle:"Recharge History",primaryHistory:rechargeRows,secondaryHistoryTitle:"Monthly Consumption",secondaryHistory:monthlyRows)
    }

    private func jsonData(_ path:String,query:[String:String],insecure:Bool) async throws -> Any? {
        var components=URLComponents(string:path)!; components.queryItems=query.map(URLQueryItem.init)
        let (data,_)=try await request(components.url!,headers:["Accept":"application/json"],insecure:insecure)
        let json=try JSONSerialization.jsonObject(with:data) as? [String:Any]; return json?["data"] is NSNull ? nil:json?["data"]
    }

    private func pgcl(_ number:String) async throws -> MeterSnapshot {
        let url=URL(string:"https://rsm.pgcl.org.bd:8443/PGCL_WEB/getCustomerWebView")!, body="customer_id=\(number.urlEncoded)".data(using:.utf8)
        let (data,_)=try await request(url,method:"POST",body:body,headers:["Content-Type":"application/x-www-form-urlencoded","X-Requested-With":"XMLHttpRequest"],insecure:true)
        let html=String(decoding:data,as:UTF8.self); guard html.contains("Customer Code") else { throw BillBoardError.notFound("PGCL customer code not found.") }
        var details:[Field]=[]
        let keys=["Customer Code","Customer Name","Address","Phone","Category","Status","Connection Date","Metered Status","Max load","Min load","Burner count","Regional Office"]
        for c in html.allTableRows() where c.count >= 2 && keys.contains(c[0]) { details.append(Field(label:c[0],value:c[1])) }
        let payments=html.tableRows(matching:"payment details").filter{$0.count >= 7}.map{c in HistoryRow(title:c[1],fields:[Field(label:"Bank",value:c[2]),Field(label:"Billed Amount",value:c[3]),Field(label:"Surcharge",value:c[4]),Field(label:"Total",value:c[5]),Field(label:"Collection Date",value:c[6])])}
        let dueMessage = html.firstMatch(#"(?is)<[^>]*class=[\"'][^\"']*alert[^\"']*[\"'][^>]*>(.*?)</[^>]+>"#)?.htmlText
        let isDue = !(dueMessage?.lowercased().contains("no due") ?? false)
        return MeterSnapshot(summary:dueMessage ?? "Payment status unavailable",isDue:isDue,customerName:details.first(where:{$0.label == "Customer Name"})?.value,lastPaymentAmount:payments.first?.fields.first(where:{$0.label == "Total"})?.value.number,lastPaymentDate:payments.first?.fields.first(where:{$0.label == "Collection Date"})?.value,details:details,primaryHistoryTitle:"Last 3 Months' Payments",primaryHistory:payments)
    }
}

final class InsecureSessionDelegate: NSObject, URLSessionDelegate, @unchecked Sendable {
    func urlSession(_ session:URLSession,didReceive challenge:URLAuthenticationChallenge,completionHandler:@escaping(URLSession.AuthChallengeDisposition,URLCredential?)->Void) {
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust, let trust=challenge.protectionSpace.serverTrust { completionHandler(.useCredential,URLCredential(trust:trust)) } else { completionHandler(.performDefaultHandling,nil) }
    }
}

private extension String {
    var urlEncoded:String { addingPercentEncoding(withAllowedCharacters:.urlQueryAllowed.subtracting(CharacterSet(charactersIn:"+&="))) ?? self }
    var htmlText:String { replacingOccurrences(of:"<[^>]+>",with:" ",options:.regularExpression).decodingHTMLEntities.collapsingWhitespace }
    var decodingHTMLEntities:String {
        replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
    }
    var collapsingWhitespace:String { replacingOccurrences(of:#"\s+"#,with:" ",options:.regularExpression).trimmingCharacters(in:.whitespacesAndNewlines) }
    var number:Double? { Double(replacingOccurrences(of:#"[^\d.\-]"#,with:"",options:.regularExpression)) }
    func firstMatch(_ pattern:String)->String? { guard let regex=try? NSRegularExpression(pattern:pattern),let match=regex.firstMatch(in:self,range:NSRange(startIndex...,in:self)),match.numberOfRanges>1,let range=Range(match.range(at:1),in:self) else{return nil};return String(self[range]) }
    func labelInputFields()->[(String,String)] { let pattern=#"(?is)<label[^>]*>(.*?)</label>(.*?)(?=<label|$)"#,inputPattern=#"(?is)<input[^>]*value=[\"']([^\"']*)[\"']"#;guard let regex=try? NSRegularExpression(pattern:pattern) else{return []};return regex.matches(in:self,range:NSRange(startIndex...,in:self)).compactMap{m in guard let lr=Range(m.range(at:1),in:self),let sr=Range(m.range(at:2),in:self) else{return nil};let s=String(self[sr]);guard let v=s.firstMatch(inputPattern),!v.isEmpty else{return nil};return(String(self[lr]).htmlText,v.decodingHTMLEntities)} }
    func allTableRows()->[[String]] { let rp=#"(?is)<tr[^>]*>(.*?)</tr>"#,cp=#"(?is)<t[dh][^>]*>(.*?)</t[dh]>"#;guard let rows=try? NSRegularExpression(pattern:rp),let cells=try? NSRegularExpression(pattern:cp) else{return []};return rows.matches(in:self,range:NSRange(startIndex...,in:self)).compactMap{m in guard let rr=Range(m.range(at:1),in:self) else{return nil};let t=String(self[rr]),v=cells.matches(in:t,range:NSRange(t.startIndex...,in:t)).compactMap{c->String? in guard let r=Range(c.range(at:1),in:t) else{return nil};return String(t[r]).htmlText};return v.isEmpty ? nil:v} }
    func tableRows(matching header:String)->[[String]] { let p=#"(?is)<table[^>]*>(.*?)</table>"#;guard let regex=try? NSRegularExpression(pattern:p) else{return []};for m in regex.matches(in:self,range:NSRange(startIndex...,in:self)){guard let r=Range(m.range(at:1),in:self) else{continue};let t=String(self[r]);if t.htmlText.localizedCaseInsensitiveContains(header){return Array(t.allTableRows().dropFirst())}};return [] }
}
private extension Array where Element == (String,String) { func value(prefix:String)->String? { first{$0.0.hasPrefix(prefix)}?.1 } }
private extension Array { subscript(safe index:Int)->Element? { indices.contains(index) ? self[index]:nil } }
