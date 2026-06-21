function toggle_component_diagram(btn) {
    var cd = document.getElementById('component-diagram');
    if (!cd) return;
    var hidden = cd.style.display === 'none';
    cd.style.display = hidden ? '' : 'none';
    btn.classList.toggle('timeline-toggle-active', hidden);
    if (hidden) {
        if (window._renderDiagramsInContainer) window._renderDiagramsInContainer(cd);
        var tl = document.getElementById('scenario-timeline');
        if (tl && tl.style.display !== 'none') {
            tl.style.display = 'none';
            var tlBtn = document.querySelector('button.timeline-toggle-active[onclick*="toggle_timeline"]');
            if (tlBtn) tlBtn.classList.remove('timeline-toggle-active');
        }
    }
}