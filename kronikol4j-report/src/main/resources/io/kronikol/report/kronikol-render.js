// Kronikol4J browser PlantUML renderer (plan §3.5 — browser-only rendering).
// Loads PlantUML-WASM (viz-global.js + plantuml.js) from the CDN, reads the embedded diagram
// sources from the #kronikol-diagrams JSON map, and renders each .plantuml-browser element.
// Uses the plantumlLoad([], callback) + plantuml.render(lines, elementId) API.
(function () {
    'use strict';

    function diagrams() {
        var el = document.getElementById('kronikol-diagrams');
        try {
            return el ? JSON.parse(el.textContent) : {};
        } catch (e) {
            return {};
        }
    }

    function renderAll() {
        var data = diagrams();
        var targets = document.querySelectorAll('.plantuml-browser');
        if (typeof plantuml === 'undefined' || typeof plantuml.render !== 'function') {
            return;
        }
        targets.forEach(function (el) {
            var source = data[el.id];
            if (!source) {
                return;
            }
            try {
                var lines = source.replace(/\r\n/g, '\n').trim().split('\n');
                plantuml.render(lines, el.id);
            } catch (e) {
                el.textContent = 'Kronikol4J: PlantUML render failed (' + e + ')';
            }
        });
    }

    function start() {
        if (typeof plantumlLoad !== 'function') {
            // WASM scripts (loaded with `defer`) not ready yet — retry shortly.
            setTimeout(start, 100);
            return;
        }
        document.body.classList.add('plantuml-ready');
        plantumlLoad([], renderAll);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
})();
