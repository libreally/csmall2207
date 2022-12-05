package cn.tedu.mall.order.service.impl;

import cn.tedu.mall.common.exception.CoolSharkServiceException;
import cn.tedu.mall.common.pojo.domain.CsmallAuthenticationInfo;
import cn.tedu.mall.common.restful.JsonPage;
import cn.tedu.mall.common.restful.ResponseCode;
import cn.tedu.mall.order.mapper.OmsCartMapper;
import cn.tedu.mall.order.service.IOmsCartService;
import cn.tedu.mall.pojo.order.dto.CartAddDTO;
import cn.tedu.mall.pojo.order.dto.CartUpdateDTO;
import cn.tedu.mall.pojo.order.model.OmsCart;
import cn.tedu.mall.pojo.order.vo.CartStandardVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;


@Slf4j
@Service
public class OmsCartServiceImpl implements IOmsCartService {
    @Autowired
    private OmsCartMapper omsCartMapper;

    //新增sku信息到购物车
    @Override
    public void addCart(CartAddDTO cartDTO) {
        // 要查询当前登录用户的购物车中是否已经包含指定商品,需要先获得当前用户id
        // 利用封装好的方法直接从SpringSecurity上下文中获取
        Long userId = getUserId();
        //根据用户ID和商品skuId查询商品
        OmsCart omsCart = omsCartMapper.selectExistsCart(userId, cartDTO.getSkuId());
        //判断查询出的osmCart是否为空
        if (omsCart==null){
            //为空则新增osmCart对象
            OmsCart newCart = new OmsCart();
            //cartAddDTO中和omsCart同名属性赋值到newCart
            BeanUtils.copyProperties(cartDTO,newCart);
            newCart.setUserId(userId);
            //执行新增
            omsCartMapper.saveCart(newCart);
        }else {
            // 如果omsCart不是null,表示当前用户购物车中已经有这个商品了
            // 我们需要做的就是将购物车中原有的数量和新增的数量相加,保存到数据库中
            // 购物车中原有的数量是omsCart.getQuantity(),新增的数量是cartDTO.getQuantity()
            // 所以我们可以将这两个数量相加的和赋值给omsCart属性
            omsCart.setQuantity(omsCart.getQuantity()+cartDTO.getQuantity());
            // 确定了数量之后,调用我们的持久层方法进行修改
            omsCartMapper.updateQuantityById(omsCart);
        }
    }

    //分页查训当前用户购物车信息
    @Override
    public JsonPage<CartStandardVO> listCarts(Integer page, Integer pageSize) {
        // 要先从SpringSecurity上下文中获得用户id
        Long userId=getUserId();
        // 执行查询之前,先设置分页条件,(page,pageSize)
        PageHelper.startPage(page,pageSize);
        // 设置完分页条件,执行查询,会自动在sql语句有添加limit关键字
        List<CartStandardVO> list=omsCartMapper.selectCartsByUserId(userId);
        // list的分页数据,实例化PageInfo对象,并转换为jsonPage返回
        return JsonPage.restPage(new PageInfo<>(list));
    }

    //批量删除购物车内容
    @Override
    public void removeCart(Long[] ids) {
        int rows = omsCartMapper.deleteCartsByIds(ids);
        if (rows==0){
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND,"删除的商品不存在或已删除");
        }
    }

    @Override
    public void removeAllCarts() {
        int rows = omsCartMapper.deleteCartsByUserId(getUserId());
        if (rows==0){
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND,"删除的商品不存在或已删除");
        }
    }

    //根据userId和skuId删除商品
    @Override
    public void removeUserCarts(OmsCart omsCart) {
        omsCartMapper.deleteCartByUserIdAndSkuId(omsCart);
    }

    // 修改购物车中商品数量的业务逻辑层方法
    @Override
    public void updateQuantity(CartUpdateDTO cartUpdateDTO) {
        // 因为执行修改的mapper方法参数是OmsCart类型
        // 所以要先实例化OmsCart
        OmsCart omsCart=new OmsCart();
        // 然后将参数cartUpdateDTO同名属性赋值到omsCart
        BeanUtils.copyProperties(cartUpdateDTO,omsCart);
        // omsCart执行修改
        omsCartMapper.updateQuantityById(omsCart);
    }
    // 业务逻辑层中有获得当前登录用户信息的需求
    // 我们的项目会在控制器方法运行前运行的过滤器代码中,解析前端传入的JWT
    // 在过滤器中,将JWT解析的结果(用户信息)保存到SpringSecurity上下文
    // 所以里可以编写代码从SpringSecurity上下文中获得用户信息
    public CsmallAuthenticationInfo getUserInfo(){
        //编写springSecurity上下文中获取用户信息代码
        UsernamePasswordAuthenticationToken authenticationToken =
                (UsernamePasswordAuthenticationToken)
                        SecurityContextHolder.getContext().getAuthentication();
        if (authenticationToken==null){
            throw new CoolSharkServiceException(ResponseCode.UNAUTHORIZED,"未授权登陆");
        }
        //从authenticationToken获取用户信息
        return (CsmallAuthenticationInfo)
                authenticationToken.getCredentials();
    }

    //获取CsmallAuthenticationInfo中的id即userid
    public Long getUserId(){
        return getUserInfo().getId();
    }


}
