all: build

build: swagger build_jar

swagger:
	wget -O sts.yaml https://app.swaggerhub.com/apiproxy/registry/longshot/STS/2.0.0?resolved=true&flatten=true&pretty=true
	swagger-codegen generate -i sts.yaml -l java -Dmodels -o .

build_jar:
	./gradlew jar