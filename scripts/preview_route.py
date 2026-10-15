"""Add one account-authenticated JSON route to the managed :9443 proxy only."""
PREVIEW_ROUTE = '''\t# Independent signed demo feed; no APKs, directory listing or task operations.
\t@ui_preview {
\t\tmethod GET
\t\tpath /desktop-remote-previews/test/latest.json
\t}
\thandle @ui_preview {
\t\troute {
\t\t\tforward_auth 127.0.0.1:28191 {
\t\t\t\turi /control-api/version
\t\t\t}
\t\t\theader Cache-Control "private, no-store"
\t\t\theader X-Content-Type-Options nosniff
\t\t\trewrite * /latest.json
\t\t\tfile_server {
\t\t\t\troot /config/ui-preview-test
\t\t\t}
\t\t}
\t}

'''

def add_preview_route(original):
    anchor = '\t@relay path /control-api/* /connector-api/* /terminal-api/* /desktop-remote-updates/*'
    if original.count(anchor) != 1 or '@ui_preview' in original or original.count('https://106.53.126.69:9443 {') != 1:
        raise ValueError('preview_proxy_requires_review')
    return original.replace(anchor, PREVIEW_ROUTE + anchor, 1)
