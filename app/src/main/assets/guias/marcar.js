// Tachar lo leido en una guia de lectura (tanda 37). Lo mete PantallaGuia
// (ui/Guia.kt) al abrirla, para que los HTML sigan siendo copias exactas de los
// artefactos. Un toque tacha y oscurece; otro lo desmarca.
//
// Las marcas van en localStorage, por guia (su <title>) y por el TEXTO de cada
// lectura, no por su posicion: si se vuelve a copiar una guia corregida, lo
// marcado sigue en su sitio mientras no cambie su titulo.
(function () {
  var clave = 'leidas:' + document.title;
  var leidas = {};
  try { leidas = JSON.parse(localStorage.getItem(clave) || '{}'); } catch (e) {}

  // Lo que se puede marcar. Green Lantern: cada lectura de un bloque y cada
  // numero de las cajas de mes. Barry y Wally: cada arco. (El `div.entry` de
  // dentro de las zonas de relevo no es un arco, por eso `article`.)
  var marcables = '.reads > li, .months li.c, article.entry';

  var estilo = document.createElement('style');
  estilo.textContent =
    marcables + ' { cursor: pointer; -webkit-tap-highlight-color: transparent; }' +
    '.leido { opacity: 0.4; }' +
    '.leido .r-title, .leido h3, li.c.leido { text-decoration: line-through; }';
  document.head.appendChild(estilo);

  function nombre(el) {
    var t = el.querySelector('.r-title, h3') || el;
    return t.textContent.replace(/\s+/g, ' ').trim();
  }

  document.querySelectorAll(marcables).forEach(function (el) {
    var id = nombre(el);
    if (leidas[id]) el.classList.add('leido');
    el.addEventListener('click', function (ev) {
      if (ev.target.closest('a')) return; // el enlace a Reddit sigue siendo enlace
      if (el.classList.toggle('leido')) leidas[id] = 1; else delete leidas[id];
      try { localStorage.setItem(clave, JSON.stringify(leidas)); } catch (e) {}
    });
  });

  // AL ABRIR, AL PRIMER APARTADO SIN TACHAR (tanda 38): lo que toca leer. Solo
  // si ya hay algo tachado; sin nada, la guia empieza por su portada. Se salta
  // lo que la propia guia esconde (Barry y Wally enseña de entrada solo los
  // imprescindibles). Otra vez en `load`, porque las fuentes de Google llegan
  // despues y mueven lo de debajo.
  function irAlSiguiente() {
    if (!document.querySelector('.leido')) return;
    var todos = document.querySelectorAll(marcables);
    for (var i = 0; i < todos.length; i++) {
      if (!todos[i].classList.contains('leido') && todos[i].offsetParent !== null) {
        todos[i].scrollIntoView({ block: 'center' });
        return;
      }
    }
  }
  irAlSiguiente();
  window.addEventListener('load', irAlSiguiente);
})();
