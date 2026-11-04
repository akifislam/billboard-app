(() => {
  const progress = document.querySelector('.scroll-progress');
  const phone = document.querySelector('.phone');
  const reveals = document.querySelectorAll('.reveal');
  const steps = [...document.querySelectorAll('.feature-step')];
  const stat = {
    icon: document.querySelector('#statIcon'), label: document.querySelector('#statLabel'),
    value: document.querySelector('#statValue'), hint: document.querySelector('#statHint')
  };
  const states = [
    ['⚡', 'Connections', 'All in one', 'NESCO · DESCO · PGCL'],
    ['◷', 'Balance estimate', '18 days', 'Based on recent usage'],
    ['●', 'Low balance', 'Quiet alerts', 'Only when attention is needed']
  ];

  const revealObserver = new IntersectionObserver(entries => {
    entries.forEach(entry => { if (entry.isIntersecting) entry.target.classList.add('visible'); });
  }, { threshold: .14 });
  reveals.forEach(el => revealObserver.observe(el));

  const stepObserver = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      steps.forEach(step => step.classList.toggle('active', step === entry.target));
      const state = states[Number(entry.target.dataset.card)];
      [stat.icon.textContent, stat.label.textContent, stat.value.textContent, stat.hint.textContent] = state;
    });
  }, { rootMargin: '-42% 0px -42% 0px' });
  steps.forEach(el => stepObserver.observe(el));

  const onScroll = () => {
    const max = document.documentElement.scrollHeight - innerHeight;
    progress.style.width = `${max ? scrollY / max * 100 : 0}%`;
    if (phone && scrollY < innerHeight) {
      const shift = Math.min(scrollY / innerHeight, 1);
      phone.style.transform = `rotateY(${-11 + shift * 11}deg) rotateX(${2 - shift * 2}deg) rotateZ(${2 - shift * 2}deg) translateY(${shift * 35}px)`;
    }
  };
  addEventListener('scroll', onScroll, { passive: true });
  onScroll();
})();
