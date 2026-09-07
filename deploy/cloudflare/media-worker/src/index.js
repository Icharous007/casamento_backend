const CACHE_CONTROL = 'public, max-age=31536000, immutable';

function allowedKey(key) {
  return key.startsWith('media/') || key.startsWith('wall/');
}

function hex(bytes) {
  return [...new Uint8Array(bytes)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

function constantTimeEquals(left, right) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return difference === 0;
}

async function authorized(url, key, secret) {
  const expires = Number(url.searchParams.get('expires'));
  const signature = url.searchParams.get('signature') || '';
  if (!Number.isSafeInteger(expires) || expires <= Math.floor(Date.now() / 1000) || !signature) {
    return false;
  }
  const cryptoKey = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'],
  );
  const expected = hex(await crypto.subtle.sign(
    'HMAC', cryptoKey, new TextEncoder().encode(`${key}\n${expires}`),
  ));
  return constantTimeEquals(signature, expected);
}

function responseHeaders(object) {
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set('ETag', object.httpEtag);
  headers.set('Accept-Ranges', 'bytes');
  headers.set('Cache-Control', CACHE_CONTROL);
  return headers;
}

function responseForObject(object, method) {
  const headers = responseHeaders(object);
  if (object.range) {
    const end = object.range.offset + object.range.length - 1;
    headers.set('Content-Length', String(object.range.length));
    headers.set('Content-Range', `bytes ${object.range.offset}-${end}/${object.size}`);
    return new Response(method === 'HEAD' ? null : object.body, { status: 206, headers });
  }
  headers.set('Content-Length', String(object.size));
  return new Response(method === 'HEAD' ? null : object.body, { headers });
}

export default {
  async fetch(request, env, ctx) {
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      return new Response('Method not allowed', { status: 405, headers: { Allow: 'GET, HEAD' } });
    }

    const url = new URL(request.url);
    let key;
    try {
      key = decodeURIComponent(url.pathname.slice(1));
    } catch {
      return new Response('Invalid media path', { status: 400 });
    }
    if (!allowedKey(key) || key.includes('..')) {
      return new Response('Not found', { status: 404 });
    }
    if (!await authorized(url, key, env.MEDIA_DELIVERY_SIGNING_KEY)) {
      return new Response('Forbidden', { status: 403 });
    }

    if (request.method === 'HEAD') {
      const object = await env.MEDIA_BUCKET.head(key);
      return object ? responseForObject(object, request.method) : new Response('Not found', { status: 404 });
    }

    const range = request.headers.get('Range');
    const cacheKey = new Request(`${url.origin}${url.pathname}`);
    if (!range) {
      const cached = await caches.default.match(cacheKey);
      if (cached) return cached;
    }

    const object = await env.MEDIA_BUCKET.get(key, range ? { range: request.headers } : undefined);
    if (!object) return new Response('Not found', { status: 404 });
    const response = responseForObject(object, request.method);
    if (!range) ctx.waitUntil(caches.default.put(cacheKey, response.clone()));
    return response;
  },
};