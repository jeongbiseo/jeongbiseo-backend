FROM eclipse-temurin:17-jre

# 타임존 Asia/Seoul 고정
RUN ln -snf /usr/share/zoneinfo/Asia/Seoul /etc/localtime

# boot jar만 복사(plain jar는 build.gradle에서 비활성)
COPY build/libs/*SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
