FROM openjdk:17

WORKDIR /usrapp/bin

ENV PORT=8080

COPY /target/classes /usrapp/bin/classes
COPY /target/dependency /usrapp/bin/dependency
COPY src/main/java/edu/escuelaing/arep/resources src/main/java/edu/escuelaing/arep/resources

CMD ["java","-cp","./classes:./dependency/*","edu.escuelaing.arep.MicroServer"]