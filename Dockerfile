FROM adoptopenjdk/openjdk8

EXPOSE 8080:8080

ADD demo-0.0.1-SNAPSHOT.jar demo.jar

ENTRYPOINT ["java","-jar","demo.jar"]
