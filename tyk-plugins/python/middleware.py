import os
import requests
import tyk

from tyk.decorators import *
from gateway import TykGateway as tyk


def get_session_id(cookie):
    #cookie = "__cfduid=d8b408707e485a2b69226296ef9bf186f1537499684; _session_id=e73c92ea9a6ac7e62505c805a9e5663e; JSESSIONID=ff5c2e57-b13f-43d9-b72b-247a544a904a"
    cookieArr = cookie.split("; ")

    for cookieEl in cookieArr:
        if cookieEl.startswith('JSESSIONID='):
            return cookieEl.split('=')[1]

    return ''

@Hook
def MyAuthMiddleware(request, session, metadata, spec):
    tyk.log("my_auth_middleware: CustomKeyCheck hook", "info")

    session_cache_host = os.getenv('SESSION_CACHE_SERVICE_HOST', '172.16.22.115')
    session_cache_port = os.getenv('SESSION_CACHE_SERVICE_PORT_HTTP', '8089')
    login_url = os.getenv('EXT_LOGIN_URL', 'http://devalto.ruckuswireless.com')
    cookie = request.get_header('Cookie')
    tyk.log('my_auth_middleware: session_cache_host=' + session_cache_host + ', session_cache_port=' + session_cache_port + ', login_url=' + login_url + ', cookie=' + cookie, 'info')

    session_id = get_session_id(cookie)
    target_url = 'http://' + session_cache_host + ':' + session_cache_port + '/session/' + session_id
    tyk.log('my_auth_middleware: sending restful call to [' + target_url + ']', 'info')

    r = requests.get(target_url)
    tyk.log('my_auth_middleware: status code: ' + str(r.status_code) + ', response body: ' + str(r.json()), 'info')

    if r.status_code == 200:
        data = r.json()
        pver = data['sessionMap']['pver']
        tenantId = data['sessionMap']['tenantId']
        tyk.log('my_auth_middleware: pver = ' + pver + ', tenantId = ' + tenantId, 'info')

        request.add_header('x-rks-pver', pver)
        request.add_header('x-rks-tenantid', tenantId)
        
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