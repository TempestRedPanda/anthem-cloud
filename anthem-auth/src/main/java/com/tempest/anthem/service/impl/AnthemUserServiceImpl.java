package com.tempest.anthem.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tempest.anthem.entity.AnthemUser;
import com.tempest.anthem.mapper.AnthemUserMapper;
import com.tempest.anthem.service.IAnthemUserService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户 服务实现类
 * </p>
 *
 * @author tempest_red_panda
 * @since 2024-08-14
 */
@Service
public class AnthemUserServiceImpl extends ServiceImpl<AnthemUserMapper, AnthemUser> implements IAnthemUserService {

    @Override
    public AnthemUser selectByUsername(String username) {
        return this.getOne(lambdaQuery().eq(AnthemUser::getAccount, username).or().eq(AnthemUser::getNickName, username));
    }
}
