/* Joe Browser - identity audit.
 *
 * A page can read a great deal about the browser it is running in. This page reads all
 * of it, compares every value with what the profile says it should be, and shows the
 * ones that disagree.
 *
 * It is the difference between "the app says the profile is consistent" and "the profile
 * has been checked, here is the result".
 */
(function () {
  'use strict';

  function expected() {
    return window.__jbExpected || {};
  }

  function value(name) {
    try {
      switch (name) {
        case 'ua': return navigator.userAgent;
        case 'platform': return navigator.platform;
        case 'language': return navigator.language;
        case 'languages': return (navigator.languages || []).join(',');
        case 'cores': return String(navigator.hardwareConcurrency);
        case 'memory': return navigator.deviceMemory === undefined ? '' : String(navigator.deviceMemory);
        case 'touch': return String(navigator.maxTouchPoints);
        case 'webdriver': return String(navigator.webdriver);
        case 'screen': return screen.width + ' x ' + screen.height;
        case 'avail': return screen.availWidth + ' x ' + screen.availHeight;
        case 'depth': return String(screen.colorDepth);
        case 'scale': return String(window.devicePixelRatio);
        case 'timezone': return Intl.DateTimeFormat().resolvedOptions().timeZone;
        case 'offset': return String(new Date().getTimezoneOffset());
        case 'glvendor': return webglValue('UNMASKED_VENDOR_WEBGL');
        case 'glrenderer': return webglValue('UNMASKED_RENDERER_WEBGL');
        case 'glversion': return webglValue('VERSION');
        case 'connection': return navigator.connection ? navigator.connection.effectiveType : '';
        case 'plugins': return String(navigator.plugins ? navigator.plugins.length : 0);
        case 'battery': return batteryValue();
        case 'voices': return voiceCount();
        default: return '';
      }
    } catch (e) {
      return '';
    }
  }

  function webglValue(which) {
    try {
      var c = document.createElement('canvas');
      var g = c.getContext('webgl') || c.getContext('experimental-webgl');
      if (!g) return '';
      if (which === 'VERSION') return g.getParameter(g.VERSION) || '';
      var e = g.getExtension('WEBGL_debug_renderer_info');
      if (!e) return '';
      return g.getParameter(e[which]) || '';
    } catch (e) {
      return '';
    }
  }

  function batteryValue() {
    try {
      if (!navigator.getBattery) return 'absent';
      return 'present';
    } catch (e) {
      return 'absent';
    }
  }

  function voiceCount() {
    try {
      if (!window.speechSynthesis) return 'absent';
      return String(speechSynthesis.getVoices().length);
    } catch (e) {
      return 'absent';
    }
  }

  /* The canvas hash is read twice. A canvas that answers differently the second time is
     not a fingerprint, it is noise, and the noise is the thing that gives it away. */
  function canvasHash() {
    try {
      function once() {
        var c = document.createElement('canvas');
        c.width = 280; c.height = 60;
        var x = c.getContext('2d');
        x.textBaseline = 'top';
        x.font = '14px Arial';
        x.fillStyle = '#f60';
        x.fillRect(4, 4, 100, 40);
        x.fillStyle = '#069';
        x.font = '15px "Segoe UI", Arial';
        x.fillText('Cwm fjordbank glyphs vext quiz', 6, 30);
        var d = x.getImageData(0, 0, 280, 60).data;
        var h = 2166136261;
        for (var i = 0; i < d.length; i++) { h ^= d[i]; h = (h * 16777619) >>> 0; }
        return h.toString(16);
      }
      var a = once();
      var b = once();
      return { value: a, stable: a === b };
    } catch (e) {
      return { value: '', stable: true };
    }
  }

  function check() {
    var exp = expected();
    var rows = [];
    function add(label, want, got) {
      if (want === '' || want === undefined || want === null) {
        rows.push({ label: label, state: 'unchecked', want: '', got: got });
      } else if (String(want) === String(got)) {
        rows.push({ label: label, state: 'ok', want: String(want), got: String(got) });
      } else {
        rows.push({ label: label, state: 'bad', want: String(want), got: String(got) });
      }
    }

    add('User agent', exp.ua, value('ua'));
    add('Platform', exp.platform, value('platform'));
    add('Language', exp.language, value('language'));
    add('Languages', exp.languages, value('languages'));
    add('Time zone', exp.timezone, value('timezone'));
    add('Clock offset', exp.offset, value('offset'));
    add('Screen', exp.screen, value('screen'));
    add('Available screen', exp.avail, value('avail'));
    add('Scale', exp.scale, value('scale'));
    add('Colour depth', exp.depth, value('depth'));
    add('CPU cores', exp.cores, value('cores'));
    add('Memory', exp.memory, value('memory'));
    add('Touch points', exp.touch, value('touch'));
    add('WebGL adapter', exp.glrenderer, value('glrenderer'));
    add('WebGL vendor', exp.glvendor, value('glvendor'));
    add('WebGL version', exp.glversion, value('glversion'));

    /* A page that finds the automation flag gets a verdict of its own. */
    var wd = value('webdriver');
    rows.push({
      label: 'Automation flag', state: wd === 'false' ? 'ok' : 'bad',
      want: 'false', got: wd
    });

    var ch = canvasHash();
    rows.push({
      label: 'Canvas', state: ch.stable ? 'ok' : 'bad',
      want: 'the same every time', got: ch.stable ? 'stable' : 'changes'
    });

    /* The engine's own leftovers are worth a look of their own. */
    var leftovers = [];
    ['__playwright', '__puppeteer_evaluation_script__', '__selenium_evaluate',
     '__webdriver_evaluate', 'callPhantom', '_phantom', '__driver_evaluate',
     '__webdriver_script_fn', '__lastWatirAlert', '__pw_manual'].forEach(function (k) {
      if (k in window) leftovers.push(k);
    });
    rows.push({
      label: 'Automation leftovers', state: leftovers.length ? 'bad' : 'ok',
      want: 'none', got: leftovers.length ? leftovers.join(', ') : 'none'
    });

    return rows;
  }

  function render() {
    var rows = check();
    var bad = rows.filter(function (r) { return r.state === 'bad'; }).length;
    var unchecked = rows.filter(function (r) { return r.state === 'unchecked'; }).length;

    var host = document.getElementById('rows');
    host.innerHTML = '';
    rows.forEach(function (r) {
      var el = document.createElement('div');
      el.className = 'row ' + r.state;
      var left = document.createElement('div');
      left.className = 'left';
      var name = document.createElement('div');
      name.className = 'name';
      name.textContent = r.label;
      left.appendChild(name);
      if (r.want && r.got && r.want !== r.got) {
        var want = document.createElement('div');
        want.className = 'sub';
        want.textContent = 'expected ' + r.want;
        left.appendChild(want);
      }
      el.appendChild(left);

      var right = document.createElement('div');
      right.className = 'right';
      var state = document.createElement('div');
      state.className = 'pill ' + r.state;
      state.textContent = r.state === 'ok' ? 'Agree' : (r.state === 'bad' ? 'Disagree' : 'Not checked');
      right.appendChild(state);
      if (r.got) {
        var got = document.createElement('div');
        got.className = 'value';
        got.textContent = r.got;
        right.appendChild(got);
      }
      el.appendChild(right);
      host.appendChild(el);
    });

    var score = document.getElementById('score');
    var total = rows.length - unchecked;
    var good = total - bad;
    var pct = total > 0 ? Math.round((good / total) * 100) : 100;
    score.textContent = pct + '%';
    document.getElementById('verdict').textContent =
      bad === 0
        ? 'Nothing disagrees with the profile.'
        : bad + (bad === 1 ? ' value disagrees' : ' values disagree') + ' with the profile.';
    document.getElementById('verdict').className = bad === 0 ? 'ok' : 'bad';
  }

  window.__jbAudit = function () { return check(); };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', render);
  } else {
    render();
  }
})();
