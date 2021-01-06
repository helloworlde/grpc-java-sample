start-server:
	gradle clean build -x test
	java -jar server/build/libs/server.jar

start-gw:
	cd gateway && \
	make tidy && \
    make generate && \
    make run

