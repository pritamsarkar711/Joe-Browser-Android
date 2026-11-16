/* Joe Browser - page runtime.
 *
 * This file is injected into every document at document start, before any page script
 * runs. It makes the page see the identity the profile was given instead of the identity
 * of the device the app happens to be running on.
 *
 * Two rules govern everything below:
 *
 *   1. A patch must be invisible. It lands on prototypes, never on instances, it keeps the
 *      enumerable flag a real WebIDL accessor has, and it prints as native code. A page
 *      that walks `navigator` with getOwnPropertyDescriptor must find nothing.
 *
 *   2. A patch must be stable. The same question asked twice gets the same answer, and
 *      the answer does not change between one read and the next. A canvas that hashes
 *      differently the second time it is read is not a fingerprint, it is a lie.
 */
(function () {
  'use strict';

  /* A runtime that fails silently is worse than one that fails loudly: the page would
     carry the host's identity and nobody would know. Any error is written to the
     document so it can be read back from the browser. */
  try {

  var F = __CFG__;
  if (!F) return;

  var SEED = (F.seed >>> 0) || 1;

  /* The page is Google's sign-in page: its risk engine scores canvas and audio tampering
     on its own properties, so the noise is turned off there rather than argued with. */
  try {
    var host = String(location.hostname || '');
    if (host === 'accounts.google.com' || host === 'accounts.youtube.com') {
      F.canvas = false;
      F.audio = false;
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Native-looking functions
   * ------------------------------------------------------------------------------------------ */

  var FN_SRC = /^\s*(?:async\s+)?function[^(]*\(([^)]*)\)/;
  var fakes = (typeof WeakMap === 'function') ? new WeakMap() : null;
  var origToString = Function.prototype.toString;

  function mark(fn, name, len, native, params) {
    try {
      if (fakes) {
        var ps = null;
        try {
          var src = null;
          if (native && fakes.has(native)) src = fakes.get(native);
          else src = native ? origToString.call(native) : origToString.call(fn);
          var m = FN_SRC.exec(src);
          if (m) ps = m[1];
        } catch (e) {}
        if (params != null) ps = params;
        fakes.set(fn, 'function ' + name + '(' + (ps == null ? '' : ps) + ') { [native code] }');
      }
      if (name) {
        try { Object.defineProperty(fn, 'name', { value: name, configurable: true }); } catch (e) {}
      }
      /* A native function has no prototype of its own. One that does is a plain
         JavaScript function with a native-looking name, and the difference is a
         single property away from being found. */
      try { delete fn.prototype; } catch (e) {}
      if (len != null) {
        try { Object.defineProperty(fn, 'length', { value: len, configurable: true }); } catch (e) {}
      }
    } catch (e) {}
    return fn;
  }

  try {
    Function.prototype.toString = mark(function toString() {
      try { if (fakes && fakes.has(this)) return fakes.get(this); } catch (e) {}
      return origToString.call(this);
    }, 'toString', 0);
  } catch (e) {}

  /* The tool runtime needs to print as native code too. */
  try {
    var MK = Symbol.for('jb.nativeMark');
    Object.defineProperty(Function.prototype, MK, {
      value: function (name, len) { return mark(this, name, len); },
      writable: true, enumerable: false, configurable: true
    });
  } catch (e) {}

  function ownerIface(obj) {
    try {
      var I = obj && obj.constructor;
      if (typeof I === 'function' && I.prototype) return I;
    } catch (e) {}
    return Object;
  }

  /* Copies a real getter onto another object, so the replacement throws "Illegal
     invocation" on a wrong receiver exactly like the original. */
  function attachGetter(obj, prop, holder) {
    try {
      var d = Object.getOwnPropertyDescriptor(holder, prop);
      if (!d || !d.get) return false;
      mark(d.get, 'get ' + prop, 0);
      Object.defineProperty(obj, prop, {
        get: d.get, set: undefined, enumerable: true, configurable: true
      });
      return true;
    } catch (e) {}
    return false;
  }

  function getter(obj, prop, value) {
    if (!obj) return;
    try {
      var I = ownerIface(obj);
      var holder = { get [prop]() {
        if (!(this instanceof I)) throw new TypeError('Illegal invocation');
        return value;
      }};
      if (attachGetter(obj, prop, holder)) return;
    } catch (e) {}
    try {
      var g = mark(() => value, 'get ' + prop, 0);
      Object.defineProperty(obj, prop, {
        get: g, set: undefined, enumerable: true, configurable: true
      });
    } catch (e2) {}
  }

  function liveGetter(obj, prop, fn) {
    if (!obj) return;
    try {
      var I = ownerIface(obj);
      var holder = { get [prop]() {
        if (!(this instanceof I)) throw new TypeError('Illegal invocation');
        return fn.call(this);
      }};
      if (attachGetter(obj, prop, holder)) return;
    } catch (e) {}
    try {
      Object.defineProperty(obj, prop, {
        get: mark(fn, 'get ' + prop, 0), configurable: true, enumerable: true
      });
    } catch (e2) {}
  }

  function method(obj, prop, fn, len) {
    try {
      var native = null, desc = null;
      try {
        desc = Object.getOwnPropertyDescriptor(obj, prop);
        var cur = obj[prop];
        if (typeof cur === 'function') native = cur;
      } catch (e) {}
      Object.defineProperty(obj, prop, {
        value: mark(fn, prop, len, native),
        writable: desc ? desc.writable !== false : true,
        enumerable: desc ? desc.enumerable === true : true,
        configurable: true
      });
    } catch (e) {}
  }

  function remove(obj, prop) {
    try { if (obj) delete obj[prop]; } catch (e) {}
  }

  /* ------------------------------------------------------------------------------------------
   * Realm guard
   * ------------------------------------------------------------------------------------------ */

  var GUARD = Symbol.for('jb.realm');
  var NP = (typeof Navigator !== 'undefined' && Navigator.prototype)
    ? Navigator.prototype
    : Object.getPrototypeOf(navigator);
  try {
    if (NP[GUARD]) return;
    Object.defineProperty(NP, GUARD, { value: 1, configurable: true });
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Noise
   * ------------------------------------------------------------------------------------------ */

  /* A stateless hash, not a running generator. The same index always gives the same
     value, which is what makes the audio and canvas fingerprints repeatable. */
  function noise(i) {
    var t = (i + SEED) | 0;
    t = Math.imul(t ^ (t >>> 15), 2246822507);
    t = Math.imul(t ^ (t >>> 13), 3266489917);
    return (((t ^ (t >>> 16)) >>> 0) % 2000) / 2000;
  }

  /* ------------------------------------------------------------------------------------------
   * Navigator
   * ------------------------------------------------------------------------------------------ */

  var isSafari = !!F.safari;
  var isFirefox = F.brand === 'Firefox';

  getter(NP, 'userAgent', F.ua);
  getter(NP, 'appVersion', String(F.ua).replace('Mozilla/', ''));
  getter(NP, 'appName', 'Netscape');
  getter(NP, 'appCodeName', 'Mozilla');
  getter(NP, 'platform', F.platform);
  getter(NP, 'vendor', isSafari ? 'Apple Computer, Inc.' : 'Google Inc.');
  getter(NP, 'vendorSub', '');
  getter(NP, 'productSub', '20030107');
  getter(NP, 'product', 'Gecko');
  getter(NP, 'language', F.language);
  /* A fresh array every read: a frozen one makes navigator.languages.push throw, and no
     browser does that. */
  liveGetter(NP, 'languages', function () { return F.languages.slice(); });
  getter(NP, 'hardwareConcurrency', F.cores);
  if (!isSafari && !isFirefox) getter(NP, 'deviceMemory', F.memory);
  getter(NP, 'maxTouchPoints', F.touch);
  getter(NP, 'webdriver', false);
  getter(NP, 'doNotTrack', F.dnt);

  /* Firefox has no deviceMemory and no userAgentData. Leaving either in place is a
     contradiction a fingerprint scan finds for free. */
  if (isSafari) {
    remove(NP, 'userAgentData');
    remove(NP, 'deviceMemory');
  }
  if (isFirefox) {
    remove(NP, 'deviceMemory');
    remove(NP, 'userAgentData');
    remove(NP, 'uaData');
    remove(NP, 'globalPrivacyControl');
  }
  if (F.brand !== 'Brave' && !isSafari) {
    remove(NP, 'brave');
    try { if (navigator.brave !== undefined) navigator.brave = undefined; } catch (e) {}
  }

  /* ------------------------------------------------------------------------------------------
   * Automation leftovers
   * ------------------------------------------------------------------------------------------ */

  try { delete Document.prototype.$cdc_asdjflasutopfhvcZLmcfl_; } catch (e) {}
  try { delete Document.prototype.$wdc_; } catch (e) {}
  try { delete window.__webdriver_evaluate; } catch (e) {}
  try { delete window.__selenium_evaluate; } catch (e) {}
  try { delete window.__webdriver_script_fn; } catch (e) {}
  try { delete window.__driver_evaluate; } catch (e) {}
  try { delete window.__lastWatirAlert; } catch (e) {}
  try { delete window.__playwright; } catch (e) {}
  try { delete window.__pw_manual; } catch (e) {}
  try { delete window.__puppeteer_evaluation_script__; } catch (e) {}
  try { delete window.callPhantom; } catch (e) {}
  try { delete window._phantom; } catch (e) {}
  try {
    if (window.Buffer && typeof window.Buffer.from === 'function' && typeof window.Buffer.alloc === 'function') {
      delete window.Buffer;
    }
  } catch (e) {}
  try {
    if (window.devtools) { try { delete window.devtools; } catch (e) { window.devtools = undefined; } }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * window.chrome
   * ------------------------------------------------------------------------------------------ */

  if (isSafari) {
    remove(window, 'chrome');
  } else if (isFirefox) {
    remove(window, 'chrome');
  } else if (!window.chrome) {
    try {
      var chromeStub = {
        app: {
          isInstalled: false,
          InstallState: {
            DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed'
          },
          RunningState: {
            CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running'
          }
        },
        runtime: {
          OnInstalledReason: {
            CHROME_UPDATE: 'chrome_update', INSTALL: 'install',
            SHARED_MODULE_UPDATE: 'shared_module_update', UPDATE: 'update'
          },
          OnRestartRequiredReason: {
            APP_UPDATE: 'app_update', OS_UPDATE: 'os_update', PERIODIC: 'periodic'
          },
          PlatformArch: {
            ARM: 'arm', ARM64: 'arm64', MIPS: 'mips', MIPS64: 'mips64',
            X86_32: 'x86-32', X86_64: 'x86-64'
          },
          PlatformOs: {
            ANDROID: 'android', CROS: 'cros', LINUX: 'linux', MAC: 'mac',
            OPENBSD: 'openbsd', WIN: 'win'
          },
          connect: mark(() => {
            throw new Error('Could not establish connection. Receiving end does not exist.');
          }, 'connect', 0),
          sendMessage: mark(() => {
            throw new Error('Could not establish connection. Receiving end does not exist.');
          }, 'sendMessage', 1)
        },
        loadTimes: mark(() => ({
          requestTime: 0, startLoad: 0, commitLoad: 0, finishDocumentLoad: 0, finishLoad: 0,
          firstPaintTime: 0, firstPaintAfterLoadTime: 0, navigationType: 'Other',
          wasFetchedViaSpdy: false, wasNpnNegotiated: true, npnNegotiatedProtocol: 'h2',
          wasAlternateProtocolAvailable: false, connectionInfo: 'h2'
        }), 'loadTimes', 0),
        csi: mark(() => ({
          startE: 0, onloadT: 0, pageT: Math.round(performance.now()),
          tran: 15
        }), 'csi', 0)
      };
      /* No chrome.runtime.id: inventing one would be its own tell. */
      Object.defineProperty(window, 'chrome', {
        value: chromeStub, writable: true, configurable: true, enumerable: true
      });
    } catch (e) {}
  }

  /* ------------------------------------------------------------------------------------------
   * navigator.userAgentData
   * ------------------------------------------------------------------------------------------ */

  function hintCopy(list) {
    var out = [];
    if (list && list.length) {
      for (var i = 0; i < list.length; i++) {
        var b = list[i] || {};
        out.push({ brand: String(b.brand || ''), version: String(b.version || '') });
      }
    }
    return out;
  }

  try {
    var uad = navigator.userAgentData;
    var uadPlatform =
      F.platform === 'Win32' ? 'Windows' :
      F.platform === 'MacIntel' ? 'macOS' :
      F.platform === 'Linux x86_64' ? 'Linux' :
      (F.platform === 'iPhone' || F.platform === 'iPad') ? 'iOS' : 'Android';

    if (uad && !isSafari) {
      var UD = Object.getPrototypeOf(uad);
      getter(UD, 'platform', uadPlatform);
      getter(UD, 'mobile', F.mobile);
      if (F.brands) liveGetter(UD, 'brands', function () { return hintCopy(F.brands); });
      var origHigh = UD.getHighEntropyValues;
      if (typeof origHigh === 'function') {
        method(UD, 'getHighEntropyValues', function getHighEntropyValues(hints) {
          return origHigh.call(this, hints).then(function (h) {
            try {
              if (F.brands) h.brands = hintCopy(F.brands);
              if (F.fullVersionList) h.fullVersionList = hintCopy(F.fullVersionList);
              if (F.uaFullVersion) h.uaFullVersion = F.uaFullVersion;
              h.platform = uadPlatform;
              h.mobile = F.mobile;
              if (F.platformVersion) h.platformVersion = F.platformVersion;
              if (F.architecture) h.architecture = F.architecture;
              if (F.bitness) h.bitness = F.bitness;
              h.model = F.mobile ? (F.model || '') : '';
              h.wow64 = !!F.wow64;
            } catch (e) {}
            return h;
          });
        }, 1);
      }
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Plugins and mime types
   *
   * A browser engine on a phone has no plugin list at all, which is not what any Chrome
   * reports. A desktop Chrome reports five PDF viewers; a mobile Chrome reports an empty
   * list. The profile's own system decides which of the two it is.
   * ------------------------------------------------------------------------------------------ */

  var PDF_PLUGINS = [
    { name: 'PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
    { name: 'Chrome PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
    { name: 'Chromium PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
    { name: 'Microsoft Edge PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
    { name: 'WebKit built-in PDF', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
  ];
  var PDF_MIME_TYPES = [
    { type: 'application/pdf', suffixes: 'pdf', description: 'Portable Document Format' },
    { type: 'text/pdf', suffixes: 'pdf', description: 'Portable Document Format' },
  ];

  function buildPluginArray(items) {
    var o = {};
    try {
      if (typeof PluginArray === 'function') o = Object.create(PluginArray.prototype);
    } catch (e) {}
    try {
      Object.defineProperty(o, 'length', { value: items.length, configurable: true, enumerable: false });
      for (var i = 0; i < items.length; i++) {
        var p = Object.create(typeof Plugin === 'function' ? Plugin.prototype : Object.prototype);
        try {
          Object.defineProperty(p, 'name', { value: items[i].name, configurable: true, enumerable: true });
          Object.defineProperty(p, 'filename', { value: items[i].filename, configurable: true, enumerable: true });
          Object.defineProperty(p, 'description', { value: items[i].description, configurable: true, enumerable: true });
          Object.defineProperty(p, 'length', { value: 1, configurable: true, enumerable: false });
        } catch (e) {}
        Object.defineProperty(o, i, { value: p, configurable: true, enumerable: true });
        Object.defineProperty(o, items[i].name, { value: p, configurable: true, enumerable: false });
      }
      o.item = mark(function item(i) { return o[i] || null; }, 'item', 1);
      o.namedItem = mark(function namedItem(n) {
        for (var k = 0; k < items.length; k++) if (items[k].name === n) return o[k];
        return null;
      }, 'namedItem', 1);
      o.refresh = mark(function refresh() {}, 'refresh', 0);
    } catch (e) {}
    return o;
  }

  function buildMimeTypeArray(items) {
    var o = {};
    try {
      if (typeof MimeTypeArray === 'function') o = Object.create(MimeTypeArray.prototype);
    } catch (e) {}
    try {
      Object.defineProperty(o, 'length', { value: items.length, configurable: true, enumerable: false });
      for (var i = 0; i < items.length; i++) {
        var m = Object.create(typeof MimeType === 'function' ? MimeType.prototype : Object.prototype);
        try {
          Object.defineProperty(m, 'type', { value: items[i].type, configurable: true, enumerable: true });
          Object.defineProperty(m, 'suffixes', { value: items[i].suffixes, configurable: true, enumerable: true });
          Object.defineProperty(m, 'description', { value: items[i].description, configurable: true, enumerable: true });
        } catch (e) {}
        Object.defineProperty(o, i, { value: m, configurable: true, enumerable: true });
        Object.defineProperty(o, items[i].type, { value: m, configurable: true, enumerable: false });
      }
      o.item = mark(function item(i) { return o[i] || null; }, 'item', 1);
      o.namedItem = mark(function namedItem(t) {
        for (var k = 0; k < items.length; k++) if (items[k].type === t) return o[k];
        return null;
      }, 'namedItem', 1);
    } catch (e) {}
    return o;
  }

  try {
    var mobileProfile = F.mobile || F.touch > 0;
    getter(NP, 'plugins', buildPluginArray(mobileProfile ? [] : PDF_PLUGINS));
    getter(NP, 'mimeTypes', buildMimeTypeArray(mobileProfile ? [] : PDF_MIME_TYPES));
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Screen
   * ------------------------------------------------------------------------------------------ */

  try {
    var SP = (typeof Screen !== 'undefined' && Screen.prototype) ? Screen.prototype : Object.getPrototypeOf(screen);
    getter(SP, 'width', F.sw);
    getter(SP, 'height', F.sh);
    getter(SP, 'availWidth', F.aw);
    getter(SP, 'availHeight', F.ah);
    getter(SP, 'colorDepth', F.cd);
    getter(SP, 'pixelDepth', F.cd);
    getter(SP, 'isExtended', false);
    getter(SP, 'availLeft', 0);
    getter(SP, 'availTop', 0);

    var SOP = (typeof ScreenOrientation !== 'undefined' && ScreenOrientation.prototype)
      ? ScreenOrientation.prototype : Object.getPrototypeOf(screen.orientation);
    getter(SOP, 'type', F.sh >= F.sw ? 'portrait-primary' : 'landscape-primary');
    getter(SOP, 'angle', 0);
  } catch (e) {}

  try { if (F.mobile) getter(window, 'ondeviceorientation', null); } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Viewport
   * ------------------------------------------------------------------------------------------ */

  try {
    Object.defineProperty(window, 'devicePixelRatio', {
      get: mark(() => F.dpr, 'get devicePixelRatio', 0),
      enumerable: true, configurable: true
    });
  } catch (e) {}

  /* The gap between the window and the viewport is measured before it is faked. Reporting
     a full screen as the outer window made the difference read as a docked developer
     panel, which every bot check looks for. */
  var gapW = 16, gapH = 140;
  try {
    gapW = Math.max(0, window.outerWidth - window.innerWidth);
    gapH = Math.max(0, window.outerHeight - window.innerHeight);
    if (!gapW) gapW = 16;
    if (!gapH) gapH = 140;
  } catch (e) {}

  try {
    liveGetter(window, 'outerWidth', function () {
      if (F.mobile || F.touch > 0) return window.innerWidth;
      return Math.min(window.innerWidth + gapW, F.sw);
    });
    liveGetter(window, 'outerHeight', function () {
      if (F.mobile || F.touch > 0) return window.innerHeight;
      return Math.min(window.innerHeight + gapH, F.ah);
    });
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Time zone
   *
   * The desktop build can set the clock through the browser itself. A WebView cannot, so
   * the whole time zone story has to live here, and leaving Date alone would hand the
   * real device clock to every page.
   * ------------------------------------------------------------------------------------------ */

  var tzOffsetCache = { key: null, value: 0 };

  function tzOffsetMinutes(tz, date) {
    var stamp = date.getTime();
    var bucket = Math.floor(stamp / 60000);
    if (tzOffsetCache.key === tz + ':' + bucket) return tzOffsetCache.value;
    var value = null;
    try {
      var dtf = new Intl.DateTimeFormat('en-US', {
        timeZone: tz, hour12: false,
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit'
      });
      var parts = dtf.formatToParts(date);
      var map = {};
      for (var i = 0; i < parts.length; i++) map[parts[i].type] = parts[i].value;
      var hour = parseInt(map.hour, 10);
      if (hour === 24) hour = 0;
      var asUTC = Date.UTC(
        parseInt(map.year, 10), parseInt(map.month, 10) - 1, parseInt(map.day, 10),
        hour, parseInt(map.minute, 10), parseInt(map.second, 10)
      );
      value = Math.round((asUTC - stamp) / 60000);
    } catch (e) {
      value = -date.getTimezoneOffset();
    }
    tzOffsetCache = { key: tz + ':' + bucket, value: value };
    return value;
  }

  function tzParts(tz, date) {
    var out = { weekday: 'Mon', month: 'Jan', day: '01', year: '1970',
                hour: '00', minute: '00', second: '00', tzName: 'UTC' };
    try {
      var dtf = new Intl.DateTimeFormat('en-US', {
        timeZone: tz, hour12: false, weekday: 'short', month: 'short',
        day: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit', second: '2-digit', timeZoneName: 'short'
      });
      var parts = dtf.formatToParts(date);
      for (var i = 0; i < parts.length; i++) {
        var t = parts[i].type, v = parts[i].value;
        if (t === 'weekday') out.weekday = v;
        else if (t === 'month') out.month = v;
        else if (t === 'day') out.day = v;
        else if (t === 'year') out.year = v;
        else if (t === 'hour') out.hour = (parseInt(v, 10) === 24 ? '00' : v);
        else if (t === 'minute') out.minute = v;
        else if (t === 'second') out.second = v;
        else if (t === 'timeZoneName') out.tzName = v;
      }
    } catch (e) {}
    return out;
  }

  try {
    method(Date.prototype, 'getTimezoneOffset', function getTimezoneOffset() {
      try { return -tzOffsetMinutes(F.tz, this); } catch (e) { return 0; }
    }, 0);

    method(Date.prototype, 'toString', function toString() {
      try {
        var p = tzParts(F.tz, this);
        var off = tzOffsetMinutes(F.tz, this);
        var sign = off >= 0 ? '+' : '-';
        var abs = Math.abs(off);
        var hh = (Math.floor(abs / 60) < 10 ? '0' : '') + Math.floor(abs / 60);
        var mm = (abs % 60 < 10 ? '0' : '') + (abs % 60);
        var time = p.hour + ':' + p.minute + ':' + p.second;
        return p.weekday + ' ' + p.month + ' ' + p.day + ' ' + p.year + ' ' + time +
               ' GMT' + sign + hh + mm + ' (' + p.tzName + ')';
      } catch (e) {
        return origToString.call(this);
      }
    }, 0);
  } catch (e) {}

  try {
    var origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
    method(Intl.DateTimeFormat.prototype, 'resolvedOptions', function resolvedOptions() {
      var o = origResolved.call(this);
      try {
        Object.defineProperty(o, 'timeZone', { value: F.tz, configurable: true, enumerable: true });
      } catch (e) {}
      return o;
    }, 0);

    /* Intl.DateTimeFormat without an explicit zone must still answer in the profile's
       zone, so the default is pinned. */
    /* The language the engine defaults to has to be the language the profile claims.
       A page that reads navigator.language and then reads Intl's own default finds two
       languages, and the second one is the one the engine could not be asked about. */
    function wrapLocale(Orig, name, withTimeZone) {
      var Wrapped = function () {
        var args = Array.prototype.slice.call(arguments);
        var locales = args.length > 0 ? args[0] : undefined;
        var options = args.length > 1 ? args[1] : undefined;
        if (locales === undefined) {
          args[0] = F.language;
        } else if (options === undefined && typeof locales === 'object' && locales !== null) {
          options = locales;
          args[0] = F.language;
        }
        if (withTimeZone) {
          if (options === undefined || options === null) {
            options = { timeZone: F.tz };
          } else if (!options.timeZone) {
            var copy = {};
            for (var k in options) copy[k] = options[k];
            copy.timeZone = F.tz;
            options = copy;
            args[1] = options;
          }
        }
        return new (Function.prototype.bind.apply(Orig, [null].concat(args)))();
      };
      Wrapped.prototype = Orig.prototype;
      try { Wrapped.supportedLocalesOf = Orig.supportedLocalesOf; } catch (e) {}
      try { Intl[name] = Wrapped; } catch (e) {}
      return Wrapped;
    }

    try {
      wrapLocale(Intl.DateTimeFormat, 'DateTimeFormat', true);
      wrapLocale(Intl.NumberFormat, 'NumberFormat', false);
      wrapLocale(Intl.Collator, 'Collator', false);
      wrapLocale(Intl.PluralRules, 'PluralRules', false);
      wrapLocale(Intl.RelativeTimeFormat, 'RelativeTimeFormat', false);
      wrapLocale(Intl.ListFormat, 'ListFormat', false);
      wrapLocale(Intl.DisplayNames, 'DisplayNames', false);
    } catch (e) {}
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Network information
   * ------------------------------------------------------------------------------------------ */

  try {
    var realConn = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
    var connProto = realConn ? Object.getPrototypeOf(realConn)
      : (typeof NetworkInformation !== 'undefined' ? NetworkInformation.prototype : null);
    if (connProto) {
      getter(connProto, 'effectiveType', '4g');
      getter(connProto, 'rtt', 40 + (SEED % 60));
      getter(connProto, 'downlink', Math.round((10 + (SEED % 40) / 10) * 10) / 10);
      getter(connProto, 'saveData', false);
      getter(connProto, 'type', 'wifi');
      getter(NP, 'connection', realConn || Object.create(connProto));
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Permissions, notifications, media devices
   * ------------------------------------------------------------------------------------------ */

  try {
    if (typeof Permissions !== 'undefined' && Permissions.prototype && Permissions.prototype.query) {
      var origQuery = Permissions.prototype.query;
      method(Permissions.prototype, 'query', function query(desc) {
        var name = desc && desc.name;
        if (name === 'geolocation' && F.geo === 'block') {
          return Promise.resolve({ state: 'denied', name: 'geolocation', onchange: null });
        }
        if (name === 'notifications') {
          return Promise.resolve({ state: 'default', name: 'notifications', onchange: null });
        }
        return origQuery.call(this, desc);
      }, 1);
    }
  } catch (e) {}

  try { if (typeof Notification !== 'undefined') getter(Notification.prototype, 'permission', 'default'); } catch (e) {}

  try {
    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
      var origEnum = navigator.mediaDevices.enumerateDevices;
      method(navigator.mediaDevices, 'enumerateDevices', function enumerateDevices() {
        return origEnum.call(this).then(function (list) {
          try {
            return (list || []).map(function (d) {
              var copy = Object.create(Object.getPrototypeOf(d) || Object.prototype);
              try {
                Object.defineProperty(copy, 'kind', { value: d.kind, configurable: true, enumerable: true });
                Object.defineProperty(copy, 'label', { value: '', configurable: true, enumerable: true });
                Object.defineProperty(copy, 'deviceId', { value: '', configurable: true, enumerable: true });
                Object.defineProperty(copy, 'groupId', { value: '', configurable: true, enumerable: true });
              } catch (e) {}
              return copy;
            });
          } catch (e) {}
          return list;
        });
      }, 0);
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * WebGL
   * ------------------------------------------------------------------------------------------ */

  try {
    if (typeof WebGLRenderingContext !== 'undefined') {
      /* The version strings have to agree with the renderer. A page that reads the
         renderer and then reads the version and finds "OpenGL ES 2.0 Chromium" under a
         claimed Mali GPU has been told the renderer was written by hand, and that is the
         whole answer to the question it was asking. */
      var gpuName = '';
      var esVersion = '';
      var d3d = '';
      try {
        var rm = String(F.glRenderer || '');
        var inner = rm.indexOf('ANGLE (') === 0 ? rm.substring(7).replace(/\)\$/, '') : rm;
        var rparts = inner.split(', ');
        gpuName = rparts.length > 1 ? rparts[1] : inner;
        var es = /OpenGL ES ([\d.]+)/.exec(rm);
        var gl = /OpenGL ([\d.]+)/.exec(rm);
        esVersion = es ? es[1] : (gl ? gl[1] : '');
        var d = /(Direct3D\d+ vs_\d+ ps_\d+)/.exec(rm);
        d3d = d ? d[1] : '';
      } catch (e) {}

      function versionFor(isWebGl2) {
        var head = isWebGl2 ? 'WebGL 2.0' : 'WebGL 1.0';
        if (d3d) return head + ' (' + d3d + ')';
        if (esVersion) {
          return head + ' (OpenGL ES ' + esVersion + (gpuName ? ' ' + gpuName : '') + ')';
        }
        if (/Metal/i.test(String(F.glRenderer || ''))) return head + ' (Metal)';
        return head + ' (OpenGL ES 3.0 ' + (gpuName || 'Chromium') + ')';
      }

      function shadingVersionFor(isWebGl2) {
        var head = 'WebGL GLSL ES 1.0';
        if (d3d) return head + ' (OpenGL ES GLSL ES 3.0 ' + d3d + ')';
        if (esVersion) {
          var major = esVersion.split('.')[0];
          return head + ' (OpenGL ES GLSL ES ' + major + '.0' + (gpuName ? ' ' + gpuName : '') + ')';
        }
        return head + ' (OpenGL ES GLSL ES 3.0 ' + (gpuName || 'Chromium') + ')';
      }

      var glProtos = [];
      if (typeof WebGLRenderingContext !== 'undefined') glProtos.push([WebGLRenderingContext.prototype, false]);
      if (typeof WebGL2RenderingContext !== 'undefined') glProtos.push([WebGL2RenderingContext.prototype, true]);

      for (var g = 0; g < glProtos.length; g++) {
        (function (proto, isGl2) {
          var glParams = {};
          if (!isSafari) {
            glParams[37445] = F.glVendor;
            glParams[37446] = F.glRenderer;
          }
          glParams[7936] = 'WebKit';
          glParams[7937] = 'WebKit WebGL';
          glParams[7938] = versionFor(isGl2);
          glParams[35724] = shadingVersionFor(isGl2);

          var origParam = proto.getParameter;
          method(proto, 'getParameter', function getParameter(pname) {
            try {
              if (isSafari && (pname === 37445 || pname === 37446)) return null;
              if (Object.prototype.hasOwnProperty.call(glParams, pname)) {
                /* The real read always runs first. It keeps the GL error state honest and
                   it costs what a real read costs: a replacement that answers from a
                   JavaScript object is hundreds of times faster than the GPU round trip,
                   and that speed is the fingerprint of the patch. */
                var realValue = origParam.call(this, pname);
                if ((pname === 37445 || pname === 37446) && realValue === null) return null;
                return glParams[pname];
              }
            } catch (e) {}
            return origParam.call(this, pname);
          }, 1);

          var origExt = proto.getExtension;
          method(proto, 'getExtension', function getExtension(name) {
            var out = origExt.call(this, name);
            if (out && name && /WEBGL_debug_renderer_info/i.test(String(name))) {
              try {
                if (isSafari) return null;
              } catch (e) {}
            }
            return out;
          }, 1);
        })(glProtos[g][0], glProtos[g][1]);
      }
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * WebGPU
   * ------------------------------------------------------------------------------------------ */

  try {
    if (typeof GPUAdapter !== 'undefined' && GPUAdapter.prototype) {
      var vendorKey = String(F.glVendor || '').toLowerCase().replace('google inc.', '');
      var adapterVendor = 'unknown';
      if (/nvidia|apple|intel|amd|radeon|qualcomm|adreno|mali|arm/.test(vendorKey)) {
        adapterVendor = (vendorKey.match(/nvidia|apple|intel|amd|radeon|qualcomm|adreno|mali|arm/) || ['unknown'])[0];
      }
      var adapterDescription = String(F.glRenderer || '');
      if (adapterDescription.indexOf('ANGLE (') === 0) {
        adapterDescription = adapterDescription.substring(7).replace(/\)$/, '');
      }
      getter(GPUAdapter.prototype, 'info', {
        vendor: adapterVendor, architecture: '', device: '', description: adapterDescription
      });
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Canvas
   * ------------------------------------------------------------------------------------------ */

  var canvasKind = (typeof WeakMap === 'function') ? new WeakMap() : null;

  /* The perturbation is keyed on the absolute pixel coordinate and applied as a
     quantisation, not as an addition. That makes it idempotent: reading the canvas,
     writing it back and reading it again gives the same image, which is what a tampering
     check does. */
  function pixelTarget(x, y, channel) {
    var h = (SEED ^ Math.imul(x + 0x9e37, 73856093) ^ Math.imul(y + 0x85eb, 19349663) ^
             Math.imul(channel + 3, 83492791)) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177) | 0;
    return ((h >>> 0) % 3) | 0;
  }

  function snap(v, x, y, channel) {
    var d = pixelTarget(x, y, channel);
    var q = v - (v % 3) + d;
    return q > 255 ? q - 3 : q;
  }

  /* Only the coverage of an edge is touched, never the colour.
     A machine with a different rasteriser draws the same colours with a slightly
     different coverage; that is the whole difference, and it is the only one worth
     faking. The colour channels are left exactly as the page drew them, because a
     changed colour is a level no rasteriser produces, and a checker that looks for
     one will find it. Solid pixels are left alone for the same reason: there is no
     coverage to disagree about.
     The change is a projection, so reading the canvas, writing it back and reading
     it again gives the same answer. */
  function perturb(data, width, height, ox, oy) {
    try {
      if (!data || !data.length) return data;
      var w = width > 0 ? width : 1;
      var count = Math.floor(data.length / 4);
      for (var p = 0; p < count; p++) {
        var i = p + 3;
        var a = data[i * 4 + 3];
        if (a <= 0 || a >= 255) continue;
        var x = (p % w) + (ox | 0), y = ((p / w) | 0) + (oy | 0);
        var d = pixelTarget(x, y, 3);
        var q = a - (a % 3) + d;
        if (q < 1) q = 1;
        if (q > 254) q = 254;
        data[i * 4 + 3] = q;
      }
    } catch (e) {}
    return data;
  }

  try {
    if (typeof HTMLCanvasElement !== 'undefined') {
      var CH = HTMLCanvasElement.prototype;
      var origGetCtx = CH.getContext;
      method(CH, 'getContext', function getContext(type) {
        var ctx = origGetCtx.apply(this, arguments);
        try {
          if (canvasKind) {
            var t = String(type || '');
            if (t.indexOf('webgl') === 0) canvasKind.set(this, t.indexOf('2') >= 0 ? 'gl2' : 'gl');
            else if (t === '2d') canvasKind.set(this, '2d');
          }
        } catch (e) {}
        return ctx;
      }, 1);

      if (typeof CanvasRenderingContext2D !== 'undefined') {
        var C2 = CanvasRenderingContext2D.prototype;
        var origData = C2.getImageData;
        method(C2, 'getImageData', function getImageData(sx, sy, sw, sh) {
          var img = origData.apply(this, arguments);
          try { perturb(img.data, img.width, img.height, sx, sy); } catch (e) {}
          return img;
        }, 4);
      }
    }
  } catch (e) {}

  /* The encoder path. A WebGL canvas without preserveDrawingBuffer reads back zeros
     through readPixels after presentation, so the noise is applied to a 2D copy instead. */
  function noisyEncode(self, encode) {
    try {
      var kind = canvasKind ? canvasKind.get(self) : null;
      if (kind === 'gl' || kind === 'gl2') return null;
      var w = self.width, h = self.height;
      if (!w || !h || w * h > 20000000) return null;
      var tmp = document.createElement('canvas');
      tmp.width = w; tmp.height = h;
      var ctx = origGetCtx.call(tmp, '2d');
      if (!ctx) return null;
      ctx.drawImage(self, 0, 0);
      var raw = origData.call(ctx, 0, 0, w, h);
      perturb(raw.data, w, h, 0, 0);
      ctx.putImageData(raw, 0, 0);
      return encode(tmp);
    } catch (e) { return null; }
  }

  try {
    if (typeof HTMLCanvasElement !== 'undefined') {
      var CH2 = HTMLCanvasElement.prototype;
      var origURL = CH2.toDataURL;
      method(CH2, 'toDataURL', function toDataURL(type, quality) {
        var a = arguments, self = this;
        var out = noisyEncode(self, function (tmp) { return origURL.apply(tmp, a); });
        return out != null ? out : origURL.apply(self, a);
      }, 0);

      var origBlob = CH2.toBlob;
      method(CH2, 'toBlob', function toBlob(callback, type, quality) {
        var self = this;
        var done = noisyEncode(self, function (tmp) {
          origBlob.call(tmp, callback, type, quality);
          return true;
        });
        if (done !== true) origBlob.call(self, callback, type, quality);
      }, 1);
    }
  } catch (e) {}

  try {
    if (typeof OffscreenCanvasRenderingContext2D !== 'undefined') {
      var OC2 = OffscreenCanvasRenderingContext2D.prototype;
      var origOffData = OC2.getImageData;
      method(OC2, 'getImageData', function getImageData(sx, sy, sw, sh) {
        var img = origOffData.apply(this, arguments);
        try { perturb(img.data, img.width, img.height, sx, sy); } catch (e) {}
        return img;
      }, 4);
    }
    if (typeof OffscreenCanvas !== 'undefined') {
      var OC = OffscreenCanvas.prototype;
      var origConv = OC.convertToBlob;
      method(OC, 'convertToBlob', function convertToBlob(type, quality) {
        var self = this;
        try {
          var w = self.width, h = self.height;
          if (!w || !h || w * h > 20000000) return origConv.apply(self, arguments);
          var tmp = new OffscreenCanvas(w, h);
          var ctx = tmp.getContext('2d');
          if (!ctx) return origConv.apply(self, arguments);
          ctx.drawImage(self, 0, 0);
          var raw = ctx.getImageData(0, 0, w, h);
          perturb(raw.data, w, h, 0, 0);
          ctx.putImageData(raw, 0, 0);
          return tmp.convertToBlob(type, quality);
        } catch (e) {
          return origConv.apply(self, arguments);
        }
      }, 2);
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Audio
   * ------------------------------------------------------------------------------------------ */

  if (F.audio) {
    try {
      if (typeof AudioBuffer !== 'undefined') {
        var origChannel = AudioBuffer.prototype.getChannelData;
        method(AudioBuffer.prototype, 'getChannelData', function getChannelData(index) {
          var arr = origChannel.apply(this, arguments);
          try {
            var q = 3e-7;
            for (var i = 0; i < arr.length; i += 1000) {
              var tgt = Math.floor(noise(i) * 3) % 3;
              var m = Math.round(arr[i] / q);
              var r = m % 3; if (r < 0) r += 3;
              var d = tgt - r;
              if (d > 1) d -= 3; else if (d < -1) d += 3;
              arr[i] = (m + d) * q;
            }
          } catch (e) {}
          return arr;
        }, 1);
      }
      if (typeof AnalyserNode !== 'undefined') {
        var origFreq = AnalyserNode.prototype.getFloatFrequencyData;
        method(AnalyserNode.prototype, 'getFloatFrequencyData', function getFloatFrequencyData(array) {
          origFreq.apply(this, arguments);
          try {
            for (var i = 0; i < array.length; i += 100) array[i] += (noise(i) - 0.5) * 1e-4;
          } catch (e) {}
        }, 1);
      }
      if (typeof AudioContext !== 'undefined') {
        getter(AudioContext.prototype, 'sampleRate', 48000);
      }
    } catch (e) {}
  }

  /* ------------------------------------------------------------------------------------------
   * Speech synthesis
   * ------------------------------------------------------------------------------------------ */

  /* A browser engine on a phone has no speech synthesis at all, and a page that asks
     for it gets an error instead of an answer. A browser that has it answers with an
     empty list and keeps quiet. The engine is given the surface so a page that asks
     does not fall over, and the voice list is the one the profile advertises. */
  try {
    if (typeof window.speechSynthesis === 'undefined') {
      var voiceList = [];
      var synthStub = {
        getVoices: mark(function getVoices() { return voiceList.slice(); }, 'getVoices', 0),
        speak: mark(function speak(u) { }, 'speak', 1),
        cancel: mark(function cancel() { }, 'cancel', 0),
        pause: mark(function pause() { }, 'pause', 0),
        resume: mark(function resume() { }, 'resume', 0),
        addEventListener: mark(function addEventListener() { }, 'addEventListener', 2),
        removeEventListener: mark(function removeEventListener() { }, 'removeEventListener', 2),
        dispatchEvent: mark(function dispatchEvent() { return true; }, 'dispatchEvent', 1),
        pending: false, speaking: false, paused: false, onvoiceschanged: null
      };
      Object.defineProperty(window, 'speechSynthesis', {
        get: mark(function () { return synthStub; }, 'get speechSynthesis', 0),
        configurable: true, enumerable: true
      });
      if (typeof window.SpeechSynthesisUtterance === 'undefined') {
        window.SpeechSynthesisUtterance = mark(function SpeechSynthesisUtterance(text) {
          this.text = text || '';
          this.lang = '';
          this.voice = null;
          this.volume = 1;
          this.rate = 1;
          this.pitch = 1;
        }, 'SpeechSynthesisUtterance', 1);
      }
    }
    var isRealSynth = false;
    try {
      isRealSynth = (typeof SpeechSynthesis !== 'undefined') &&
        (typeof window.speechSynthesis !== 'undefined') &&
        (window.speechSynthesis instanceof SpeechSynthesis);
    } catch (e) {}

    if (isRealSynth) {
      var VOICE_TABLE = {
        en: [['Alex', 'en-US'], ['Samantha', 'en-US'], ['Daniel', 'en-GB'], ['Karen', 'en-AU']],
        de: [['Anna', 'de-DE'], ['Daniel', 'de-DE'], ['Anna', 'de-AT'], ['Anna', 'de-CH']],
        fr: [['Thomas', 'fr-FR'], ['Amelie', 'fr-CA'], ['Audrey', 'fr-FR']],
        es: [['Monica', 'es-ES'], ['Paulina', 'es-MX'], ['Jorge', 'es-ES']],
        pt: [['Luciana', 'pt-BR'], ['Joana', 'pt-PT'], ['Daniel', 'pt-BR']],
        it: [['Alice', 'it-IT'], ['Federico', 'it-IT']],
        nl: [['Samantha', 'nl-NL'], ['Lotte', 'nl-NL']],
        pl: [['Zofia', 'pl-PL'], ['Ewa', 'pl-PL']],
        sv: [['Alex', 'sv-SE'], ['Emma', 'sv-SE']],
        da: [['Nanna', 'da-DK'], ['Marie', 'da-DK']],
        nb: [['Ola', 'nb-NO'], ['Ida', 'nb-NO']],
        fi: [['Katri', 'fi-FI'], ['Sampo', 'fi-FI']],
        tr: [['Emel', 'tr-TR'], ['Filiz', 'tr-TR']],
        ru: [['Milena', 'ru-RU'], ['Pavel', 'ru-RU']],
        uk: [['Ivanna', 'uk-UA'], ['Dmytro', 'uk-UA']],
        ar: [['Maged', 'ar-SA'], ['Zeina', 'ar-SA']],
        he: [['Yael', 'he-IL'], ['Eitan', 'he-IL']],
        hi: [['Lekha', 'hi-IN'], ['Neerja', 'hi-IN']],
        ja: [['Kyoko', 'ja-JP'], ['Otoya', 'ja-JP']],
        ko: [['Yuna', 'ko-KR'], ['Sora', 'ko-KR']],
        zh: [['Tingting', 'zh-CN'], ['Sinji', 'zh-HK']],
        id: [['Damayanti', 'id-ID'], ['Gadis', 'id-ID']],
        th: [['Kanya', 'th-TH'], ['Narisa', 'th-TH']],
        vi: [['Linh', 'vi-VN'], ['Mai', 'vi-VN']],
        ms: [['Yasmin', 'ms-MY'], ['Siti', 'ms-MY']]
      };
      var baseLang = String(F.language || 'en').split('-')[0].toLowerCase();
      var voices = VOICE_TABLE[baseLang] || VOICE_TABLE.en;
      var proto = Object.getPrototypeOf(speechSynthesis);
      method(proto, 'getVoices', function getVoices() {
        return voices.map(function (v) {
          var o = Object.create(SpeechSynthesisVoice.prototype);
          try {
            Object.defineProperty(o, 'name', { value: v[0], configurable: true, enumerable: true });
            Object.defineProperty(o, 'voiceURI', { value: v[0], configurable: true, enumerable: true });
            Object.defineProperty(o, 'lang', { value: v[1], configurable: true, enumerable: true });
            Object.defineProperty(o, 'localService', { value: true, configurable: true, enumerable: true });
            Object.defineProperty(o, 'default', { value: false, configurable: true, enumerable: true });
          } catch (e) {}
          return o;
        });
      }, 0);
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Fonts
   * ------------------------------------------------------------------------------------------ */

  try {
    var wantSet = F.fontSet && F.fontSet !== 'auto' ? F.fontSet
      : (/mac/i.test(F.os) ? 'macos'
        : (/linux/i.test(F.os) ? 'linux'
          : (/android/i.test(F.os) ? 'android'
            : (/iphone|ipad/i.test(F.os) ? 'ios' : 'windows'))));
    var FONT_SETS = __FONTS__;
    var fams = FONT_SETS[wantSet] || FONT_SETS.windows;

    function fontEntry(family) {
      var o = { family: family, fullName: family, postscriptName: family.replace(/ /g, ''),
                style: 'Regular', weight: '400' };
      try {
        o.blob = function () { return Promise.resolve(new Blob([])); };
      } catch (e) {}
      return o;
    }

    /* A desktop browser can ask which fonts are installed; a phone browser cannot. The
       profile has to answer the same way the browser it claims would answer, because a
       list of fonts that the font check then contradicts is a contradiction. */
    var advertisesFonts = !F.mobile;

    if (advertisesFonts) {
      method(window, 'queryLocalFonts', function queryLocalFonts() {
        return Promise.resolve(fams.map(fontEntry));
      }, 0);
      if (navigator.fonts) {
        var origFontQuery = navigator.fonts.query;
        method(navigator.fonts, 'query', function query() {
          try {
            return Promise.resolve(fams.map(fontEntry));
          } catch (e) {
            return origFontQuery.apply(this, arguments);
          }
        }, 0);
      }
    }

    /* The font check is the classic way to find out which fonts a machine has: ask
       whether a face is installed. A machine that advertises one font list and answers
       the check with another is a machine with two answers, and the check is what finds
       it. The answer has to follow the list the profile publishes. */
    var GENERIC_FAMILIES = ['serif', 'sans-serif', 'monospace', 'cursive', 'fantasy',
      'system-ui', 'ui-serif', 'ui-sans-serif', 'ui-monospace', 'ui-rounded', 'math',
      'emoji', 'fangsong', 'inherit', 'initial', 'unset', 'revert', '-webkit-body'];
    var fontFamilies = fams.map(function (f) { return String(f).toLowerCase(); });

    function familyFromShorthand(shorthand) {
      var text = String(shorthand || '');
      var quoted = /["']([^"']+)["']/.exec(text);
      if (quoted) return quoted[1].toLowerCase();
      var stripped = text.replace(/^\s*\d+(\.\d+)?(px|pt|em|rem|%)?\s*(normal|italic|oblique|small-caps|bold|bolder|lighter|\d{3})\s*/, '');
      var parts = stripped.split(',');
      var family = (parts[parts.length - 1] || '').trim().toLowerCase();
      return family.replace(/\s+/g, ' ');
    }

    if (advertisesFonts && document.fonts && typeof document.fonts.check === 'function') {
      var origFontCheck = document.fonts.check;
      method(document.fonts, 'check', function check(font, text) {
        try {
          var family = familyFromShorthand(font);
          if (!family) return origFontCheck.apply(this, arguments);
          if (GENERIC_FAMILIES.indexOf(family) >= 0) return true;
          var known = fontFamilies.indexOf(family) >= 0;
          if (known) return true;
          /* A family the profile does not publish is not on the machine either. */
          return false;
        } catch (e) {
          return origFontCheck.apply(this, arguments);
        }
      }, 2);
    }

    if (typeof CanvasRenderingContext2D !== 'undefined') {
      var origMeasure = CanvasRenderingContext2D.prototype.measureText;
      method(CanvasRenderingContext2D.prototype, 'measureText', function measureText(text) {
        var m = origMeasure.apply(this, arguments);
        try {
          if (F.fonts) {
            var s = String(text || '');
            var h = 0;
            for (var k = 0; k < s.length; k++) h = (h * 31 + s.charCodeAt(k)) | 0;
            var jitter = (noise(h) - 0.5) * 0.06;
            Object.defineProperty(m, 'width', {
              value: Math.max(0, m.width + jitter), configurable: true
            });
          }
        } catch (e) {}
        return m;
      }, 1);
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Battery
   * ------------------------------------------------------------------------------------------ */

  if (F.battery && !F.mobile) {
    try {
      if (!navigator.getBattery) {
        method(NP, 'getBattery', function getBattery() {
          var fake = Object.create(typeof BatteryManager !== 'undefined' ? BatteryManager.prototype : Object.prototype);
          try {
            Object.defineProperty(fake, 'level', {
              value: Math.min(1, 0.35 + (SEED % 60) / 100), configurable: true, enumerable: true
            });
            Object.defineProperty(fake, 'charging', { value: true, configurable: true, enumerable: true });
            Object.defineProperty(fake, 'chargingTime', { value: 1800, configurable: true, enumerable: true });
            Object.defineProperty(fake, 'dischargingTime', { value: Infinity, configurable: true, enumerable: true });
          } catch (e) {}
          try {
            fake.addEventListener = function () {};
            fake.removeEventListener = function () {};
          } catch (e) {}
          return Promise.resolve(fake);
        }, 0);
      }
    } catch (e) {}
  }

  /* ------------------------------------------------------------------------------------------
   * WebRTC
   * ------------------------------------------------------------------------------------------ */

  try {
    var webrtc = String(F.webrtc || 'off').toLowerCase();
    var strictRtc = webrtc !== 'public' && webrtc !== 'system';

    if (webrtc === 'off' || webrtc === 'disabled') {
      if (navigator.mediaDevices && typeof navigator.mediaDevices.getUserMedia === 'function') {
        method(navigator.mediaDevices, 'getUserMedia', function getUserMedia() {
          return Promise.reject(new DOMException('Permission denied', 'NotAllowedError'));
        }, 1);
      }
    }

    if (typeof RTCPeerConnection !== 'undefined') {
      var PRIVATE = /^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.|169\.254\.|127\.|::1|fe80:|f[cd])/;

      function cleanSdp(sdp) {
        try {
          if (!sdp) return sdp;
          var lines = String(sdp).split(/\r?\n/);
          var kept = [];
          for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            if (line.indexOf('a=candidate:') === 0) {
              var m = /typ\s+(host|srflx|prflx|relay)/.exec(line);
              var addr = /([0-9a-fA-F:.]+)\s/.exec(line.slice(line.indexOf('candidate:') + 10));
              if (m && m[1] === 'host') continue;
              if (addr && PRIVATE.test(addr[1])) continue;
              if (strictRtc && /a=end-of-candidates/.test(line)) continue;
            }
            kept.push(line);
          }
          return kept.join('\r\n');
        } catch (e) { return sdp; }
      }

      if (typeof RTCPeerConnection.prototype.setLocalDescription === 'function') {
        var origSetLocal = RTCPeerConnection.prototype.setLocalDescription;
        method(RTCPeerConnection.prototype, 'setLocalDescription', function setLocalDescription(desc) {
          try {
            if (desc && desc.sdp) desc = Object.create(Object.getPrototypeOf(desc), {
              sdp: { value: cleanSdp(desc.sdp), enumerable: true }
            });
          } catch (e) {}
          return origSetLocal.apply(this, arguments);
        }, 1);
      }

      ['createOffer', 'createAnswer'].forEach(function (name) {
        try {
          var orig = RTCPeerConnection.prototype[name];
          if (typeof orig !== 'function') return;
          method(RTCPeerConnection.prototype, name, function () {
            return orig.apply(this, arguments).then(function (desc) {
              try {
                if (desc && desc.sdp) desc.sdp = cleanSdp(desc.sdp);
              } catch (e) {}
              return desc;
            });
          }, 0);
        } catch (e) {}
      });

      if (typeof RTCPeerConnection.prototype.addEventListener === 'function') {
        var origAdd = RTCPeerConnection.prototype.addEventListener;
        method(RTCPeerConnection.prototype, 'addEventListener', function addEventListener(type, fn, opts) {
          if (type === 'icecandidate' && typeof fn === 'function') {
            var wrapped = function (ev) {
              if (ev && ev.candidate) return;
              return fn.call(this, ev);
            };
            return origAdd.call(this, type, wrapped, opts);
          }
          return origAdd.apply(this, arguments);
        }, 2);
      }
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Element rectangles
   * ------------------------------------------------------------------------------------------ */

  try {
    if (typeof Element !== 'undefined') {
      var rectCache = (typeof WeakMap === 'function') ? new WeakMap() : null;
      var origGBCR = Element.prototype.getBoundingClientRect;
      method(Element.prototype, 'getBoundingClientRect', function getBoundingClientRect() {
        var r = origGBCR.apply(this, arguments);
        try {
          var dj = rectCache ? rectCache.get(this) : null;
          if (!dj) {
            var tag = '';
            try { tag = String(this.tagName || ''); } catch (e0) {}
            var h = (SEED ^ Math.imul(tag.length + 3, 2654435761)) | 0;
            h = Math.imul(h ^ (h >>> 13), 1274126177) | 0;
            dj = [((h >>> 0) % 7) - 3, (((h >>> 3) % 7) - 3)];
            if (rectCache) rectCache.set(this, dj);
          }
          r.x += dj[0] / 1000;
          r.y += dj[1] / 1000;
        } catch (e) {}
        return r;
      }, 0);
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * matchMedia
   * ------------------------------------------------------------------------------------------ */

  function matchesResolution(query) {
    var s = String(query);
    var m = /\(min-resolution:\s*([\d.]+)\s*(dppx|dpcm|dpi)\s*\)/.exec(s);
    if (m) {
      var css = parseFloat(m[1]) * (m[2] === 'dpcm' ? 2.54 : 1) / (m[2] === 'dpi' ? 96 : 1);
      return F.dpr >= css;
    }
    m = /\(max-resolution:\s*([\d.]+)\s*(dppx|dpcm|dpi)\s*\)/.exec(s);
    if (m) {
      var css2 = parseFloat(m[1]) * (m[2] === 'dpcm' ? 2.54 : 1) / (m[2] === 'dpi' ? 96 : 1);
      return F.dpr <= css2;
    }
    return null;
  }

  var SBW = (typeof F.sbw === 'number') ? F.sbw : 17;
  var overlayBars = (F.mobile || F.touch > 0) || SBW === 0;

  try {
    var origMM = window.matchMedia;
    method(window, 'matchMedia', function matchMedia(query) {
      var real = origMM.call(this, query);
      var res = matchesResolution(query);
      var s = String(query);
      var bar = null;
      if (/scrollbar-width:\s*none/.test(s)) bar = overlayBars;
      else if (/scrollbar-width:\s*thin/.test(s)) bar = !overlayBars && SBW !== 17;
      else if (/scrollbar-width:\s*auto/.test(s)) bar = !overlayBars && SBW === 17;

      if (res === null && bar === null) return real;

      var q = String(query);
      var matches = res !== null ? res : !!bar;
      try {
        var mql = Object.create(Object.getPrototypeOf(real) || Object.prototype);
        Object.defineProperty(mql, 'media', {
          get: function () { return q; }, configurable: true, enumerable: true
        });
        Object.defineProperty(mql, 'matches', {
          get: function () { return matches; }, configurable: true, enumerable: true
        });
        ['addEventListener', 'removeEventListener', 'addListener', 'removeListener',
         'dispatchEvent', 'onchange'].forEach(function (k) {
          if (typeof real[k] === 'function') {
            mql[k] = function () { return real[k].apply(real, arguments); };
          }
        });
        return mql;
      } catch (e) {
        return real;
      }
    }, 1);
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Scrollbar gutter
   * ------------------------------------------------------------------------------------------ */

  try {
    if (typeof Element !== 'undefined' && Element.prototype) {
      ['clientWidth', 'clientHeight'].forEach(function (prop) {
        var desc = Object.getOwnPropertyDescriptor(Element.prototype, prop);
        if (!desc || !desc.get) return;
        var real = desc.get;
        Object.defineProperty(Element.prototype, prop, {
          configurable: true,
          enumerable: desc.enumerable,
          get: mark(function () {
            var v = 0;
            try { v = real.call(this); } catch (e) { return 0; }
            if (typeof v !== 'number' || v <= 0) return v;
            var scrolls = false;
            try {
              var cs = (this.ownerDocument && this.ownerDocument.defaultView)
                ? this.ownerDocument.defaultView.getComputedStyle(this) : null;
              var oy = cs ? String(cs.overflowY || '') : '';
              var ox = cs ? String(cs.overflowX || '') : '';
              var vertical = (oy === 'auto' || oy === 'scroll' || oy === 'overlay') &&
                this.scrollHeight > (this.clientHeight || 0);
              var horizontal = (ox === 'auto' || ox === 'scroll' || ox === 'overlay') &&
                this.scrollWidth > (this.clientWidth || 0);
              scrolls = vertical || horizontal;
            } catch (e) { scrolls = false; }
            return Math.max(0, v - (scrolls ? SBW : 0));
          }, 'get ' + prop, 0)
        });
      });
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Worker realm
   *
   * A worker has no window and no document, so it needs its own copy of the navigator
   * patches. A Blob that carries JavaScript is the only door into a worker, so the Blob
   * constructor is wrapped to prepend the worker runtime.
   * ------------------------------------------------------------------------------------------ */

  try {
    if (F.wjs && typeof window.Blob === 'function') {
      var OrigBlob = window.Blob;
      var JbBlob = mark(function Blob(parts, options) {
        try {
          var t = options && options.type ? String(options.type) : '';
          if (/javascript|ecmascript/i.test(t) && parts && parts.length) {
            var list = Array.prototype.slice.call(parts);
            list.unshift(F.wjs);
            parts = list;
          }
        } catch (e) {}
        return new OrigBlob(parts, options);
      }, 'Blob', 2, OrigBlob);
      try { JbBlob.prototype = OrigBlob.prototype; } catch (e) {}
      window.Blob = JbBlob;
    }
  } catch (e) {}

  /* ------------------------------------------------------------------------------------------
   * Done
   * ------------------------------------------------------------------------------------------ */

  /* ------------------------------------------------------------------------------------------
   * The language every request carries
   *
   * The engine asks for the language of the device. The profile claims a language of its
   * own, and a page that reads the language out of the script and then compares it with
   * the language the server received finds two languages in one session. The request
   * carries the profile's language, whatever the request is.
   * ------------------------------------------------------------------------------------------ */
  try {
    var ACC = F.accept;
    if (ACC) {
      var origFetch = window.fetch;
      if (typeof origFetch === 'function') {
        var WrappedFetch = function fetch(input, init) {
          try {
            init = init || {};
            var src = (init.headers !== undefined && init.headers !== null)
              ? init.headers
              : (input && typeof input === 'object' ? input.headers : null);
            var headers = new Headers(src || {});
            if (!headers.has('accept-language')) headers.set('Accept-Language', ACC);
            init.headers = headers;
          } catch (e) {}
          return origFetch.call(this, input, init);
        };
        WrappedFetch.prototype = origFetch.prototype;
        try { WrappedFetch.supportedLocalesOf = origFetch.supportedLocalesOf; } catch (e) {}
        window.fetch = mark(WrappedFetch, 'fetch', 2, origFetch);
      }

      if (typeof XMLHttpRequest !== 'undefined') {
        var origOpen = XMLHttpRequest.prototype.open;
        var origSend = XMLHttpRequest.prototype.send;
        method(XMLHttpRequest.prototype, 'open', function open() {
          try { this.__jbLang = ACC; } catch (e) {}
          return origOpen.apply(this, arguments);
        }, 2, origOpen);
        method(XMLHttpRequest.prototype, 'send', function send(body) {
          try {
            if (this.__jbLang) this.setRequestHeader('Accept-Language', this.__jbLang);
          } catch (e) {}
          return origSend.apply(this, arguments);
        }, 1, origSend);
      }
    }
  } catch (e) {}

  try {
    document.documentElement.setAttribute('data-jb-runtime', '1');
  } catch (e) {}

  } catch (err) {
    try {
      var msg = String(err && err.message ? err.message : err);
      document.documentElement.setAttribute('data-jb-error', msg);
      if (window.console && console.warn) console.warn('[Joe Browser] fingerprint runtime: ' + msg);
    } catch (e) {}
  }
})();
