package com.tempest.anthem.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.tempest.anthem.entity.AnthemUser;

/**
 * <p>
 * 用户 服务类
 * </p>
 *
 * @author tempest_red_panda
 * @since 2024-08-14
 */
public interface IAnthemUserService extends IService<AnthemUser> {

    /**
     *
     * 根据用户名查询用户信息
     */
    AnthemUser selectByUsername(String username);
}
