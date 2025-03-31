package com.tempest.anthem.service.impl;


import com.tempest.anthem.entity.AnthemUser;
import com.tempest.anthem.service.IAnthemUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 用户详情
 *
 * @author tempest_red_panda
 * @since 2024-08-15
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final IAnthemUserService anthemUserService;

    @Autowired
    public UserDetailsServiceImpl(IAnthemUserService anthemUserService) {
        this.anthemUserService = anthemUserService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {

        AnthemUser anthemUser = anthemUserService.selectByUsername(username);
        List<SimpleGrantedAuthority> grantedAuthorityList = Stream.of("USER").map(SimpleGrantedAuthority::new).collect(Collectors.toList());

        return new User(username, anthemUser.getPassword(), grantedAuthorityList);
    }
}
