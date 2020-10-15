FROM adoptopenjdk/openjdk8

EXPOSE 8080:8080

ADD spring-petclinic-2.3.0.BUILD-SNAPSHOT.jar demo.jar

ENTRYPOINT ["java","-jar","demo.jar"]
