package com.jeongbiseo.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

/**
 * 통합 테스트 공용 MySQL 컨테이너 베이스임. 통합 테스트 클래스는 이 클래스를 상속해 컨테이너를 공유함.
 *
 * 컨테이너를 클래스마다 띄우면 콜드 스타트를 그만큼 지불하고, 각 클래스의 @ServiceConnection 필드가 서로 다른
 * ServiceConnectionContextCustomizer를 만들어 Spring TestContext 캐시 키까지 갈라짐(캐시 키는
 * connectionName· connectionDetailsTypes·컨테이너 인스턴스 동일성으로 판정함). 필드를 이 베이스 한 곳에 두면 인스턴스가
 * 하나라 키가 합쳐지고 컨텍스트도 재사용됨.
 *
 * ponytail: 정리는 Testcontainers Ryuk에 맡기고 stop()을 호출하지 않음. 컨텍스트가 여럿 공유하는 컨테이너를 어느 한쪽이 종료하면
 * 나머지가 깨지므로 JVM 수명 동안 살려 둠.
 */
public abstract class MySqlContainerSupport {

	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

	static {
		MYSQL.start();
	}

}
