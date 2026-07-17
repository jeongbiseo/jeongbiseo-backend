package com.jeongbiseo.domain.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jeongbiseo.domain.member.entity.Member;

/**
 * 회원 저장소임. name에 UNIQUE가 없어(동명이인 허용, v1.4) 이름 기준 조회 파생 쿼리는 두지 않음. 회원 식별은 항상 id로 함.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

}
