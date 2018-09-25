import os
import requests
import tyk

from tyk.decorators import *
from gateway import TykGateway as tyk


def get_session_id(cookie):
    #cookie = "JSESSIONID=ff5c2e57-b13f-43d9-b72b-247a544a904a"
    cookieArr = cookie.split("; ")

    for cookieEl in cookieArr:
        if cookieEl.startswith('JSESSIONID='):
            return cookieEl.split('=')[1]

    return ''

@Hook
def MyAuthMiddleware(request, session, metadata, spec):
    tyk.log("my_auth_middleware: CustomKeyCheck hook", "info")

    server_host = os.getenv('TYK_PLUGIN_SERVER_SERVICE_HOST', '80.80.80.80')
    session_cache_port = os.getenv('TYK_PLUGIN_SERVER_PORT_HTTP', '8080')
    login_url = os.getenv('EXT_LOGIN_URL', 'http://www.google.com')
    cookie = request.get_header('Cookie')
    tyk.log('my_auth_middleware: server_host=' + server_host + ', server_port=' + server_port + ', login_url=' + login_url + ', cookie=' + cookie, 'info')

    session_id = get_session_id(cookie)
    target_url = 'http://' + server_host + ':' + server_port + '/session/' + session_id
    tyk.log('my_auth_middleware: sending restful call to [' + target_url + ']', 'info')

    r = requests.get(target_url)
    tyk.log('my_auth_middleware: status code: ' + str(r.status_code) + ', response body: ' + str(r.json()), 'info')

    if r.status_code == 200:
        data = r.json()
        pver = data['sessionMap']['x-custom-version']
        tenantId = data['sessionMap']['x-custom-tenant-id']
        tyk.log('my_auth_middleware: pver = ' + pver + ', tenantId = ' + tenantId, 'info')

        request.add_header('x-custom-version', pver)
        request.add_header('x-custom-tenant-id', tenantId)
        
        #//creating session object means it's authenticated for the request
        session.rate = 1000.0
        session.per = 1.0
        metadata["token"] = session_id
    else:
        tyk.log('my_auth_middleware: forward to login URL' + login_url, 'info')
        #//forward to login URL when it's unauthenticated
        request.object.return_overrides.response_code = 301
        request.object.return_overrides.headers['Location'] = login_url
        request.object.return_overrides.response_error = 'Not authorized (Python middleware)'

    return request, session, metadata