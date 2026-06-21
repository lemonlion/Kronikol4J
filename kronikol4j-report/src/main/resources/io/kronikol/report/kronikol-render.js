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
        var targets = Array.prototype.slice.call(document.querySelectorAll('.plantuml-browser'));
        if (typeof plantuml === 'undefined' || typeof plantuml.render !== 'function') {
            return;
        }
        // Render strictly one diagram at a time: PlantUML-WASM (TeaVM) renders asynchronously and uses
        // global state, so concurrent render() calls clobber each other — a report with 2+ diagrams
        // (e.g. the component diagram + per-test sequence diagrams) would lose all but one. Wait for
        // each diagram's <svg> to be injected (MutationObserver) before starting the next.
        var i = 0;
        function next() {
            if (i >= targets.length) {
                return;
            }
            var el = targets[i++];
            var source = data[el.id];
            if (!source) {
                next();
                return;
            }
            var settled = false;
            var observer = new MutationObserver(function () {
                if (el.querySelector('svg')) {
                    finish();
                }
            });
            var timer = setTimeout(finish, 15000); // never stall the queue if one render hangs
            function finish() {
                if (settled) {
                    return;
                }
                settled = true;
                observer.disconnect();
                clearTimeout(timer);
                next();
            }
            observer.observe(el, { childList: true, subtree: true });
            try {
                var lines = source.replace(/\r\n/g, '\n').trim().split('\n');
                plantuml.render(lines, el.id);
                if (el.querySelector('svg')) {
                    finish(); // synchronous render — don't wait on the observer
                }
            } catch (e) {
                el.textContent = 'Kronikol4J: PlantUML render failed (' + e + ')';
                finish();
            }
        }
        next();
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
