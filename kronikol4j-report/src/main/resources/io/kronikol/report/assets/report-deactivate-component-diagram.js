                               if (hidden) {
                                   var cd = document.getElementById('component-diagram');
                                   if (cd && cd.style.display !== 'none') {
                                       cd.style.display = 'none';
                                       var cdBtn = document.querySelector('button.timeline-toggle-active[onclick*="toggle_component_diagram"]');
                                       if (cdBtn) cdBtn.classList.remove('timeline-toggle-active');
                                   }
                               }