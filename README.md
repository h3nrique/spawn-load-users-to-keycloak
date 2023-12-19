# Import User to Keycloak

Java demo project using [Spawn](https://github.com/eigr/spawn) to load users from csv (or not) to Keycloak instance.

Special thanks to [Adriano Santos](https://github.com/sleipnir) and [Elias Dal Ben](https://github.com/eliasdarruda), without your help there would be no success with this project.

## Running locally

### Start `Spawn Proxy` and some dependencies

```bash
docker network create spawn-user-demo
docker-compose up mariadb nats spawn
```

### Start Keycloak instance (if necessary)

```bash
docker-compose -f docker-compose-keycloak.yaml up -d
```

### Start Jaeger instance (if necessary)

```bash
# docker-compose -f docker-compose-monitoring.yaml up -d # TODO :: Pending param config.
```

### Start Java `ActorHost` application

```bash
docker-compose up app
```

### Test

```bash
# Import user
curl -v -H 'Content-Type: application/json' -d '{ "username":  "33333333333", "firstName": "Jane Child3", "lastName":  "Doe", "email": "janechild1@example.com", "birthDate":  "1967-05-03", "mobile": "(21) 93333-3333", "cityIbgeId":  "3304557", "uf": "RJ" }' 'http://localhost:8080/import'

# Get import status
curl -v 'http://localhost:8080/import/status/33333333333'
```

## Deploy on Kubernetes

```bash
kubectl create ns eigr-functions
curl -L https://github.com/eigr/spawn/releases/download/v1.1.1/manifest.yaml | kubectl create -n eigr-functions -f -
kubectl create ns example
kubectl create -f mysql.yaml -n example
kubectl create secret generic mysql-example-secret -n eigr-functions --from-literal=database=eigr --from-literal=host='mysql.example.svc.cluster.local' --from-literal=port='3306' --from-literal=username='admin' --from-literal=password='admin' --from-literal=encryptionKey=$(openssl rand -base64 32)
kubectl create -f nats.yaml -n example
kubectl create -f actorsystem.yaml -n example
# Build image and push to a private/public registry
# mvn clean compile -Pimage-build jib:dockerBuild
# TODO :: Next steps??
```

## Deploy on Openshift with Openshift Pipeline (Tekton)

```bash
oc create ns eigr-functions
curl -L https://github.com/eigr/spawn/releases/download/v1.1.1/manifest.yaml | oc apply -n eigr-functions -f -
oc create ns example
oc process openshift//mysql-persistent -p MYSQL_USER=admin -p MYSQL_PASSWORD=admin -p MYSQL_ROOT_PASSWORD=mypassword -p MYSQL_DATABASE=eigr | oc create -n example -f -
oc create secret generic mysql-example-secret -n eigr-functions --from-literal=database=eigr --from-literal=host='mysql.example.svc.cluster.local' --from-literal=port='3306' --from-literal=username='admin' --from-literal=password='admin' --from-literal=encryptionKey=$(openssl rand -base64 32)
oc create -f nats.yaml -n example
oc create -f actorsystem.yaml -n example
oc create -f openshift-pipeline.yaml -n example
oc create role actorhost-pipeline --verb='*' --resource='actorhosts,actorsystems' -n example
oc create rolebinding actorhost-pipeline --role=actorhost-pipeline --serviceaccount=spawn:pipeline -n example
# If you want to expose actor service with route command
oc create route edge import --service=spawn-load-users-to-keycloak --port=8080 -n example
```
