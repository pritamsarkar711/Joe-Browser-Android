/* Joe Browser - worker runtime.
 *
 * A worker has no window and no document, but it still reports a user agent, still knows
 * a time zone and still draws to a canvas. A page that hashes a canvas in the main thread
 * and a worker that hashes the same drawing must agree, or the mismatch is a fingerprint
 * on its own.
 *
 * This file is prepended to every JavaScript Blob, which is the only door into a worker
 * from a page. The seed and the coordinate arithmetic are identical to the page runtime,
 * so both realms produce the same answer.
 */
(function () {
  'use strict';

  var F = __CFG__;
  if (!F) return;

  var SEED = (F.seed >>> 0) || 1;
  var GUARD = Symbol.for('jb.realm');

  function mark(fn, name, len) {
    try {
      if (name) Object.defineProperty(fn, 'name', { value: name, configurable: true });
      if (len != null) Object.defineProperty(fn, 'length', { value: len, configurable: true });
    } catch (e) {}
    return fn;
  }

  function getter(obj, prop, value) {
    if (!obj) return;
    try {
      var g = mark(function () { return value; }, 'get ' + prop, 0);
      Object.defineProperty(obj, prop, {
        get: g, set: undefined, enumerable: true, configurable: true
      });
    } catch (e) {}
  }

  function liveGetter(obj, prop, fn) {
    if (!obj) return;
    try {
      Object.defineProperty(obj, prop, {
        get: mark(fn, 'get ' + prop, 0), configurable: true, enumerable: true
      });
    } catch (e) {}
  }

  function method(obj, prop, fn, len) {
    try {
      Object.defineProperty(obj, prop, {
        value: mark(fn, prop, len),
        writable: true, enumerable: true, configurable: true
      });
    } catch (e) {}
  }

  var NP = (typeof WorkerNavigator !== 'undefined' && WorkerNavigator.prototype)
    ? WorkerNavigator.prototype
    : (typeof navigator !== 'undefined' ? Object.getPrototypeOf(navigator) : null);
  if (!NP) return;
  try {
    if (NP[GUARD]) return;
    Object.defineProperty(NP, GUARD, { value: 1, configurable: true });
  } catch (e) {}

  var isSafari = !!F.safari;

  getter(NP, 'userAgent', F.ua);
  getter(NP, 'appVersion', String(F.ua).replace('Mozilla/', ''));
  getter(NP, 'platform', F.platform);
  getter(NP, 'language', F.language);
  liveGetter(NP, 'languages', function () { return F.languages.slice(); });
  getter(NP, 'hardwareConcurrency', F.cores);
  if (!isSafari && F.brand !== 'Firefox') getter(NP, 'deviceMemory', F.memory);
  getter(NP, 'maxTouchPoints', F.touch);
  getter(NP, 'webdriver', false);

  try {
    var origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
    method(Intl.DateTimeFormat.prototype, 'resolvedOptions', function resolvedOptions() {
      var o = origResolved.call(this);
      try {
        Object.defineProperty(o, 'timeZone', { value: F.tz, configurable: true, enumerable: true });
        Object.defineProperty(o, 'locale', { value: F.language, configurable: true, enumerable: true });
      } catch (e) {}
      return o;
    }, 0);

    try {
      var OrigNum = Intl.NumberFormat;
      var WrappedNum = function NumberFormat(locales, options) {
        var args = Array.prototype.slice.call(arguments);
        if (args.length === 0 || args[0] === undefined) args[0] = F.language;
        return new (Function.prototype.bind.apply(OrigNum, [null].concat(args)))();
      };
      WrappedNum.prototype = OrigNum.prototype;
      Intl.NumberFormat = WrappedNum;
    } catch (e) {}
  } catch (e) {}

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

  function perturb(data, width, height, ox, oy) {
    try {
      if (!data || !data.length) return data;
      var w = width > 0 ? width : 1;
      var count = Math.floor(data.length / 4);
      for (var p = 0; p < count; p++) {
        var a = data[p * 4 + 3];
        if (a <= 0 || a >= 255) continue;
        var x = (p % w) + (ox | 0), y = ((p / w) | 0) + (oy | 0);
        var d = pixelTarget(x, y, 3);
        var q = a - (a % 3) + d;
        if (q < 1) q = 1;
        if (q > 254) q = 254;
        data[p * 4 + 3] = q;
      }
    } catch (e) {}
    return data;
  }

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

  try {
    if (typeof WebGLRenderingContext !== 'undefined') {
      var glParams = {};
      if (!isSafari) {
        glParams[37445] = F.glVendor;
        glParams[37446] = F.glRenderer;
      }
      glParams[7936] = 'WebKit';
      glParams[7937] = 'WebKit WebGL';

      var protos = [WebGLRenderingContext.prototype];
      if (typeof WebGL2RenderingContext !== 'undefined') protos.push(WebGL2RenderingContext.prototype);

      for (var i = 0; i < protos.length; i++) {
        (function (proto) {
          var origParam = proto.getParameter;
          method(proto, 'getParameter', function getParameter(pname) {
            try {
              if (isSafari && (pname === 37445 || pname === 37446)) return null;
              if (Object.prototype.hasOwnProperty.call(glParams, pname)) {
                var realValue = origParam.call(this, pname);
                if ((pname === 37445 || pname === 37446) && realValue === null) return null;
                return glParams[pname];
              }
            } catch (e) {}
            return origParam.call(this, pname);
          }, 1);
        })(protos[i]);
      }
    }
  } catch (e) {}
})();
