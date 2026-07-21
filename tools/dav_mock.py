#!/usr/bin/env python3
"""
Minimal but RFC-compliant-enough CardDAV + CalDAV + VTODO mock server (stdlib only).

Implements just enough of the DAV protocol for UnifiedComms' sync engines to exercise
their REAL production HTTP paths (PROPFIND principal/home-set scan, GET/PUT/DELETE of
vCard/VCalendar resources). This is what was missing from the repo: the existing
ContactSyncE2ETest / TaskSyncE2ETest reference carddav_mock.py / taskdav_mock.py that
were never shipped, so the DAV *write* round-trip was never proven.

Run on host:  python3 dav_mock.py 8088
Then:        adb reverse tcp:8088 tcp:8088   (so emulator sees it at 127.0.0.1:8088)

Endpoints (all Basic-auth "tester"/"secret"):
  /                        -> DAV root (current-user-principal)
  /addressbooks/           -> addressbook-home-set, contains 1 addressbook
  /addressbooks/default/   -> CardDAV collection (.vcf resources)
  /calendars/              -> calendar-home-set, contains 1 calendar + 1 task list
  /calendars/default/      -> CalDAV calendar collection (.ics, VEVENT)
  /calendars/tasks/        -> task list collection (.ics, VTODO)
"""
import base64
import hashlib
import os
import re
import sys
import threading
import urllib.parse
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, HTTPServer, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8088
USER, PASS = "tester", "secret"

NS = {
    "D": "DAV:",
    "C": "urn:ietf:params:xml:ns:caldav",
    "A": "urn:ietf:params:xml:ns:carddav",
    "CS": "http://calendarserver.org/ns/",
}

# In-memory store: path -> (content_type, body_bytes)
STORE = {}
# primitive etag = sha1 of body
def etag_of(body: bytes) -> str:
    return '"' + hashlib.sha1(body).hexdigest()[:16] + '"'

def auth_ok(headers):
    h = headers.get("Authorization", "")
    if not h.startswith("Basic "):
        return False
    try:
        dec = base64.b64decode(h[6:]).decode()
        u, p = dec.split(":", 1)
        return u == USER and p == PASS
    except Exception:
        return False

def multistatus(responses):
    """responses: list of (href, props_dict). props: name->value; value is str or ET.Element."""
    import xml.etree.ElementTree as ET
    root = ET.Element("{" + NS["D"] + "}multistatus")
    for href, props in responses:
        resp = ET.SubElement(root, "{" + NS["D"] + "}response")
        h = ET.SubElement(resp, "{" + NS["D"] + "}href")
        h.text = href
        propstat = ET.SubElement(resp, "{" + NS["D"] + "}propstat")
        prop = ET.SubElement(propstat, "{" + NS["D"] + "}prop")
        for name, value in props.items():
            el = ET.SubElement(prop, name)
            if isinstance(value, ET.Element):
                el.append(value)
            elif isinstance(value, list):
                for child in value:
                    el.append(child)
            elif value is not None:
                el.text = value
        st = ET.SubElement(propstat, "{" + NS["D"] + "}status")
        st.text = "HTTP/1.1 200 OK"
    return ET.tostring(root, encoding="utf-8")

def qname(ns, tag):
    return ET.QName("{" + NS[ns] + "}", tag)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass  # quiet

    def _send(self, code, body=b"", ctype="application/xml; charset=utf-8", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        if extra:
            for k, v in extra.items():
                self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_PROPFIND(self):
        if not auth_ok(self.headers):
            self._send(401, b"", extra={"WWW-Authenticate": 'Basic realm="dav"'})
            return
        depth = self.headers.get("Depth", "1")
        path = urllib.parse.urlparse(self.path).path
        body = self._read_body()

        # DAV property responses: CalDAV client's findPropHref() scans the flat
        # ParsedProp list for <prop> then the NEXT <href> element after it, so
        # property values MUST be wrapped in <D:href> — plain text won't match.
        def _href(url):
            el = ET.Element(ET.QName("{" + NS["D"] + "}", "href"))
            el.text = url
            return el

        # current-user-principal probe (answer from ANY path — real servers do)
        if self._wants_prop(body, "current-user-principal"):
            self._send(207, multistatus([(path, {qname("D", "current-user-principal"): [_href("/Principal/")]})]))
            return
        # home-set probe (answer from ANY path). Response href must equal the
        # requested home-set value, because the engine's parseHrefsFromPropfind()
        # takes .firstOrNull() (the first <href> in document order).
        if self._wants_prop(body, "addressbook-home-set"):
            self._send(207, multistatus([
                ("/addressbooks/", {qname("A", "addressbook-home-set"): [_href("/addressbooks/")]})
            ]))
            return
        if self._wants_prop(body, "calendar-home-set"):
            self._send(207, multistatus([
                ("/calendars/", {qname("C", "calendar-home-set"): [_href("/calendars/")]})
            ]))
            return
        if path.rstrip("/") in ("", "/Principal"):
            # home sets (also wraps values in <D:href> for findPropHref compat)
            self._send(207, multistatus([
                ("/Principal/", {
                    qname("A", "addressbook-home-set"): [_href("/addressbooks/")],
                    qname("C", "calendar-home-set"): [_href("/calendars/")],
                })
            ]))
            return
        if path.rstrip("/") == "/addressbooks":
            self._send(207, multistatus([
                ("/addressbooks/", {qname("D", "resourcetype"): "",
                                   qname("D", "displayname"): "Addressbooks"}),
                ("/addressbooks/default/", {
                    "resourcetype": self._rt(["addressbook", "collection"]),
                    "displayname": "Contacts",
                }),
            ]))
            return
        if path.rstrip("/") == "/calendars":
            self._send(207, multistatus([
                ("/calendars/", {qname("D", "resourcetype"): ""}),
                ("/calendars/default/", {
                    "resourcetype": self._rt(["calendar", "collection"]),
                    "displayname": "Calendar",
                    qname("C", "supported-calendar-component-set"): self._comp("VEVENT"),
                }),
                ("/calendars/tasks/", {
                    "resourcetype": self._rt(["calendar", "collection"]),
                    "displayname": "Tasks",
                    qname("C", "supported-calendar-component-set"): self._comp("VTODO"),
                }),
            ]))
            return
        # collection listing (addressbook or calendar) -> existing items + collection self
        items = [(p, c, t) for p, (c, t) in STORE.items()
                 if p.startswith(path.rstrip("/") + "/") and not p.endswith("/")]
        props = []
        # self
        if path.rstrip("/") == "/addressbooks/default":
            props.append((path, {qname("D", "resourcetype"): "",
                                 qname("D", "displayname"): "Contacts"}))
        elif path.rstrip("/") == "/calendars/default":
            props.append((path, {qname("D", "resourcetype"): "",
                                 qname("D", "displayname"): "Calendar"}))
        elif path.rstrip("/") == "/calendars/tasks":
            props.append((path, {qname("D", "resourcetype"): "",
                                 qname("D", "displayname"): "Tasks"}))
        for p, ctype, body_b in items:
            props.append((p, {qname("D", "getetag"): etag_of(body_b)}))
        self._send(207, multistatus(props))

    def do_GET(self):
        if not auth_ok(self.headers):
            self._send(401, b"", extra={"WWW-Authenticate": 'Basic realm="dav"'})
            return
        path = urllib.parse.urlparse(self.path).path
        if path in STORE:
            ctype, body = STORE[path]
            self._send(200, body, ctype, extra={"ETag": etag_of(body)})
        else:
            self._send(404)

    def do_PUT(self):
        if not auth_ok(self.headers):
            self._send(401, b"", extra={"WWW-Authenticate": 'Basic realm="dav"'})
            return
        path = urllib.parse.urlparse(self.path).path
        body = self._read_body()
        ctype = self.headers.get("Content-Type", "text/vcard")
        STORE[path] = (ctype, body)
        self._send(201, b"", extra={"ETag": etag_of(body)})

    def do_DELETE(self):
        if not auth_ok(self.headers):
            self._send(401, b"", extra={"WWW-Authenticate": 'Basic realm="dav"'})
            return
        path = urllib.parse.urlparse(self.path).path
        if path in STORE:
            del STORE[path]
            self._send(204)
        else:
            self._send(404)

    def _read_body(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        return self.rfile.read(length) if length else b""

    def _wants_prop(self, body, local):
        if not body:
            return False
        return local.encode() in body

    def _comp(self, name):
        # Return just <C:comp name="..."/> — multistatus() wraps it in
        # <C:supported-calendar-component-set>. Doing both double-nests.
        return [ET.Element(qname("C", "comp"), {"name": name})]

    def _rt(self, types):
        # resourcetype CHILD elements (addressbook/calendar/collection). These get
        # appended directly inside the <resourcetype> property element in multistatus,
        # so DO NOT wrap them in another <resourcetype> (that double-nests and breaks
        # the client's resourcetype walk).
        out = []
        for t in types:
            ns = "A" if t == "addressbook" else ("C" if t == "calendar" else "D")
            out.append(ET.Element(qname(ns, t)))
        return out


def main():
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"DAV mock listening on 127.0.0.1:{PORT} (user={USER})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
