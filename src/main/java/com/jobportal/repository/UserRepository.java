package com.jobportal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.jobportal.model.User;
public interface UserRepository extends JpaRepository<User, Long>{

    List<User> findAllByEmail(String email);

    List<User> findAllByEmailIgnoreCase(String email);
}
