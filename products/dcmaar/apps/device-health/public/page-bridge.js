(() => {
  try {
    if (window.__dcmaar__bridgeInstalled) {
      return;
    }
    window.__dcmaar__bridgeInstalled = true;
    window.__dcmaarEvents = window.__dcmaarEvents || [];

    window.addEventListener('message', (event) => {
      const data = event?.data;
      if (!data || !data.__dcmaar) {
        return;
      }
      try {
        window.__dcmaarEvents = window.__dcmaarEvents || [];
        window.__dcmaarEvents.push(data.event);
        if (window.__dcmaarEvents.length > 50) {
          window.__dcmaarEvents.splice(0, window.__dcmaarEvents.length - 50);
        }
      } catch (bridgeErr) {
        console.warn('DCMAAR bridge error', bridgeErr);
      }
    });
  } catch (initErr) {
    console.warn('DCMAAR bridge init error', initErr);
  }
})();
