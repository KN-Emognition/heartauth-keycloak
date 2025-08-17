## Required Envs (deprecated):

```
KEYCLOAK_REALM=myrealm
KEYCLOAK_CLIENT_ID=myclient
KEYCLOAK_REDIRECT_URI=http://localhost:3000/*
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin

```

test link for login
```
http://localhost:8080/realms/heartauth/protocol/openid-connect/auth?client_id=APP-ID&
response_type=code&redirect_uri=http://localhost:8081/&scope=openid&prompt=login&max_age=0
```