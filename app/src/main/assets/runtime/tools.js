/* Joe Browser - built-in tools.
 *
 * These are part of the app itself, not an extension. The desktop build ships them as
 * unpacked extensions because a desktop browser has no other way to reach into a page;
 * here the app owns the page runtime, so the same features are simply part of it.
 *
 *   - Paste as human types the clipboard one character at a time, with the pauses a
 *     person actually takes, and dispatches the real keyboard events a form listens for.
 *   - The proxy guard notices when the public address changes or the connection dies and
 *     puts a banner on the page.
 *   - The right-click guard restores the context menu, copying and selection on pages
 *     that block them.
 */
(function () {
  'use strict';

  try {

  var CFG = __TOOLS__;
  if (!CFG) return;

  var MARK = 'data-jb-tools';

  /* ----------------------------------------------------------------------------------------
   * Typing
   * ------------------------------------------------------------------------------------------ */

  function delays() {
    var s = String(CFG.speed || 'natural');
    if (s === 'relaxed') return [90, 240];
    if (s === 'fast') return [18, 60];
    if (s === 'instant') return [0, 6];
    return [38, 135];
  }

  function rand(min, max) {
    return min + Math.random() * (max - min);
  }

  function isTypingTarget(el) {
    if (!el || el.nodeType !== 1) return false;
    var tag = el.tagName;
    if (tag === 'TEXTAREA') return !el.disabled && !el.readOnly;
    if (tag === 'INPUT') {
      var t = String(el.type || 'text').toLowerCase();
      if (t === 'checkbox' || t === 'radio' || t === 'button' || t === 'submit' || t === 'reset' || t === 'file') return false;
      return !el.disabled && !el.readOnly;
    }
    if (el.isContentEditable) return true;
    return false;
  }

  function lastFocused() {
    var el = document.activeElement;
    if (isTypingTarget(el)) return el;
    if (CFG.lastTarget && isTypingTarget(CFG.lastTarget) && document.contains(CFG.lastTarget)) {
      return CFG.lastTarget;
    }
    return null;
  }

  function fireKey(el, type, key, code, keyCode, text) {
    var ev;
    try {
      ev = new KeyboardEvent(type, {
        key: key,
        code: code,
        keyCode: keyCode,
        which: keyCode,
        charCode: type === 'keypress' ? keyCode : 0,
        bubbles: true,
        cancelable: true,
        composed: true,
        view: window
      });
    } catch (e) {
      ev = document.createEvent('Event');
      ev.initEvent(type, true, true);
    }
    try { Object.defineProperty(ev, 'keyCode', { get: function () { return keyCode; } }); } catch (e) {}
    try { Object.defineProperty(ev, 'which', { get: function () { return keyCode; } }); } catch (e) {}
    el.dispatchEvent(ev);
    return ev;
  }

  function fireInput(el, text, inputType) {
    try {
      var ev = new InputEvent('input', {
        bubbles: true,
        cancelable: false,
        composed: true,
        data: text,
        inputType: inputType || 'insertText'
      });
      el.dispatchEvent(ev);
    } catch (e) {}
  }

  function fireBeforeInput(el, text, inputType) {
    try {
      var ev = new InputEvent('beforeinput', {
        bubbles: true,
        cancelable: true,
        composed: true,
        data: text,
        inputType: inputType || 'insertText'
      });
      el.dispatchEvent(ev);
    } catch (e) {}
  }

  var typing = false;

  /**
   * Types a string into the focused element.
   *
   * The text is inserted through the editing commands so the field's own undo stack and
   * change events behave, and every keystroke is dispatched as well so a page that counts
   * key presses sees the same number a person would have produced.
   */
  function typeText(el, text) {
    if (!el || !text) return Promise.resolve();
    if (typing) return Promise.resolve();
    typing = true;

    var range = CFG.delayRange || delays();
    var chars = Array.from(String(text));
    var i = 0;
    var chain = Promise.resolve();

    function step() {
      if (i >= chars.length) {
        typing = false;
        return;
      }
      var ch = chars[i++];
      var code = ch.length === 1 ? 'Key' + ch.toUpperCase() : 'Unidentified';
      if (ch === '\n') code = 'Enter';

      fireKey(el, 'keydown', ch, code, ch.length === 1 ? ch.charCodeAt(0) : 13);
      fireKey(el, 'keypress', ch, code, ch.length === 1 ? ch.charCodeAt(0) : 13);
      fireBeforeInput(el, ch, 'insertText');

      var inserted = false;
      try {
        if (el.isContentEditable) {
          document.execCommand('insertText', false, ch);
          inserted = true;
        } else if (typeof el.setRangeText === 'function' && typeof el.selectionStart === 'number') {
          var start = el.selectionStart;
          var end = el.selectionEnd;
          el.setRangeText(ch, start, end, 'end');
          inserted = true;
        } else {
          inserted = document.execCommand('insertText', false, ch);
        }
      } catch (e) {
        inserted = false;
      }

      if (!inserted) {
        try {
          el.value = (el.value || '') + ch;
        } catch (e) {}
      }

      fireInput(el, ch, 'insertText');
      fireKey(el, 'keyup', ch, code, ch.length === 1 ? ch.charCodeAt(0) : 13);

      var wait = rand(range[0], range[1]);
      /* A person pauses after a space and after punctuation. */
      if (ch === ' ' || '.,;:!?'.indexOf(ch) >= 0) {
        if (Math.random() < 0.28) wait += rand(130, 450);
      }
      /* And very occasionally stops altogether. */
      if (Math.random() < 0.012) wait += rand(180, 560);

      chain = chain.then(function () {
        return new Promise(function (resolve) { setTimeout(resolve, wait); });
      }).then(step);
    }

    try { el.focus(); } catch (e) {}
    step();
    return chain;
  }

  /* The clipboard arrives through the paste event, which carries it without asking the
     page for any permission. */
  document.addEventListener('paste', function (e) {
    if (!CFG.humanPaste) return;
    var el = e.target;
    if (!isTypingTarget(el)) return;
    var text = '';
    try {
      if (e.clipboardData) text = e.clipboardData.getData('text/plain') || '';
    } catch (err) {}
    if (!text) return;
    e.preventDefault();
    e.stopPropagation();
    CFG.lastTarget = el;
    typeText(el, text);
  }, true);

  document.addEventListener('focusin', function (e) {
    if (isTypingTarget(e.target)) CFG.lastTarget = e.target;
  }, true);

  /* The app calls this from the toolbar. It is the same path the paste event takes. */
  window.__jbPasteText = function (text) {
    var el = lastFocused();
    if (!el) return false;
    typeText(el, text);
    return true;
  };
  window.__jbPasteNow = function () {
    var el = lastFocused();
    if (!el) return false;
    try {
      if (navigator.clipboard && navigator.clipboard.readText) {
        navigator.clipboard.readText().then(function (text) {
          if (text) typeText(el, text);
        }, function () {});
        return true;
      }
    } catch (e) {}
    return false;
  };

  /* ----------------------------------------------------------------------------------------
   * Right click, copy and selection
   * ------------------------------------------------------------------------------------------ */

  if (CFG.rightClick) {
    var BLOCKED = { contextmenu: 1, copy: 1, cut: 1, selectstart: 1, dragstart: 1, mousedown: 1, mouseup: 1 };

    /* A page that blocks the context menu does it on the capture phase, so the guard has
       to be earlier still. */
    window.addEventListener('contextmenu', function (e) {
      if (e && e.isTrusted) e.stopImmediatePropagation();
    }, true);
    window.addEventListener('copy', function (e) {
      if (e && e.isTrusted) e.stopImmediatePropagation();
    }, true);
    window.addEventListener('cut', function (e) {
      if (e && e.isTrusted) e.stopImmediatePropagation();
    }, true);
    window.addEventListener('selectstart', function (e) {
      if (e && e.isTrusted) e.stopImmediatePropagation();
    }, true);

    try {
      var style = document.createElement('style');
      style.textContent = '*{-webkit-user-select:text !important;user-select:text !important;}';
      (document.head || document.documentElement).appendChild(style);
    } catch (e) {}
  }

  /* ----------------------------------------------------------------------------------------
   * Proxy guard
   * ------------------------------------------------------------------------------------------ */

  var banner = null;

  function showBanner(kind, title, message, sticky) {
    try {
      if (!banner) {
        banner = document.createElement('div');
        banner.id = 'jb-banner';
        banner.style.cssText = [
          'position:fixed', 'left:12px', 'right:12px', 'bottom:12px', 'z-index:2147483647',
          'font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif',
          'font-size:13px', 'line-height:1.45', 'padding:12px 14px', 'border-radius:10px',
          'box-shadow:0 8px 28px rgba(0,0,0,.28)', 'display:flex', 'gap:10px',
          'align-items:flex-start', 'transition:opacity .2s ease'
        ].join(';');
        (document.body || document.documentElement).appendChild(banner);
      }
      var tone = kind === 'bad' ? '#c33744' : (kind === 'warn' ? '#f2a81b' : '#1f8a5f');
      banner.style.background = tone;
      banner.style.color = '#ffffff';
      banner.innerHTML = '';
      var box = document.createElement('div');
      box.style.cssText = 'flex:1';
      var h = document.createElement('div');
      h.style.cssText = 'font-weight:600;margin-bottom:2px';
      h.textContent = title;
      var p = document.createElement('div');
      p.style.cssText = 'opacity:.92';
      p.textContent = message;
      box.appendChild(h);
      box.appendChild(p);
      banner.appendChild(box);
      if (!sticky) {
        setTimeout(function () {
          try { if (banner) { banner.style.opacity = '0'; } } catch (e) {}
        }, 6000);
      }
    } catch (e) {}
  }

  if (CFG.watch && CFG.hasProxy) {
    var KEY = 'jb.proxy.state';
    var state = { baseline: '', fails: 0, offline: false };
    try {
      var stored = localStorage.getItem(KEY);
      if (stored) state = JSON.parse(stored) || state;
    } catch (e) {}

    function save() {
      try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {}
    }

    function probe() {
      var urls = ['https://ipwho.is/', 'https://api.ipify.org?format=json'];
      var i = 0;
      function next() {
        if (i >= urls.length) return Promise.resolve(null);
        var url = urls[i++];
        var ctrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
        var timer = ctrl ? setTimeout(function () { ctrl.abort(); }, 8000) : null;
        return fetch(url, { cache: 'no-store', signal: ctrl ? ctrl.signal : undefined })
          .then(function (r) { if (timer) clearTimeout(timer); return r.ok ? r.json() : null; })
          .then(function (j) { return (j && j.ip) ? { ip: j.ip, country: j.country || j.country_name || '' } : null; })
          .catch(function () { return null; })
          .then(function (r) { return r || next(); });
      }
      return next();
    }

    function check() {
      probe().then(function (cur) {
        if (!cur) {
          state.fails++;
          if (state.fails >= 2 && !state.offline) {
            state.offline = true;
            state.fails = 0;
            save();
            showBanner('bad', 'Proxy appears offline',
              'The profile could not reach the address service. Check the proxy before you sign in.', true);
          }
          return;
        }
        state.fails = 0;
        if (!state.baseline) {
          state.baseline = cur.ip;
          save();
          return;
        }
        if (state.baseline !== cur.ip) {
          var from = state.baseline;
          state.baseline = cur.ip;
          save();
          showBanner('warn', 'IP address rotated',
            from + ' → ' + cur.ip + (cur.country ? ' (' + cur.country + ')' : '') +
            '. Pause account activity and check the session.', true);
          return;
        }
        if (state.offline) {
          state.offline = false;
          save();
          showBanner('info', 'Proxy is back online', cur.ip + (cur.country ? ' · ' + cur.country : ''), false);
        }
      });
    }

    setTimeout(check, 3000);
    setInterval(check, Math.max(30000, (CFG.interval || 60) * 1000));
  }

  try {
    document.documentElement.setAttribute(MARK, '1');
  } catch (e) {}

  } catch (err) {
    try {
      var msg = String(err && err.message ? err.message : err);
      document.documentElement.setAttribute('data-jb-tools-error', msg);
      if (window.console && console.warn) console.warn('[Joe Browser] tools runtime: ' + msg);
    } catch (e) {}
  }
})();
