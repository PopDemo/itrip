package cn.itrip.auth.controller;

import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import cn.itrip.auth.exception.TokenValidationFailedException;
import cn.itrip.auth.service.TokenService;
import cn.itrip.auth.service.UserService;
import cn.itrip.beans.dto.Dto;
import cn.itrip.beans.pojo.ItripUser;
import cn.itrip.beans.vo.ItripTokenVO;
import cn.itrip.beans.vo.ItripWechatTokenVO;
import cn.itrip.common.DtoUtil;
import cn.itrip.common.ErrorCode;
import cn.itrip.common.UrlUtils;

import com.alibaba.fastjson.JSON;

/**
 * 第三方登录控制器
 * @author hduser
 *
 */
@Controller
@RequestMapping(value = "/vendors")
public class VendorsController {

	@Resource
	private UserService userService;
	@Resource
	private TokenService tokenService;
	
	/**
	 * 微信登录——第一步：获取code
	 * @param response
	 */
	@RequestMapping(value = "/wechat/login")
	public void wechatLogin(HttpServletResponse response){
		String qrconnect="https://open.weixin.qq.com/connect/qrconnect?appid=wx9168f76f000a0d4c&redirect_uri=http%3a%2f%2fitrip.project.bdqn.cn%2fauth%2fvendors%2fwechat%2fcallback&response_type=code&scope=snsapi_login&state=STATE#wechat_redirect";
		try {
			response.sendRedirect(qrconnect);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/**
	 * 微信登录——第二步：通过code换取access_token
	 * @param code
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	@RequestMapping(value = "/wechat/callback")
	public void wechatCallback(@RequestParam String code,HttpServletRequest request,HttpServletResponse response) throws IOException{
		String accessTokenUrl="https://api.weixin.qq.com/sns/oauth2/access_token?appid=wx9168f76f000a0d4c&secret=8ba69d5639242c3bd3a69dffe84336c1&code="+
				code+"&grant_type=authorization_code";	
		response.setContentType("text/html;charset=utf-8");
		String json=UrlUtils.loadURL(accessTokenUrl);
		Map<String,Object> wechatToken=JSON.parseObject(json, Map.class);
		
		try {
			//验证本地库是否存在该用户
			ItripUser user=userService.findByUsername(wechatToken.get("openid").toString());
			if(user==null){//如果不存在则添加用户
				user=new ItripUser();
				user.setUserCode(wechatToken.get("openid").toString());//微信用户openid作为本地库用户名
				user.setUserType(1);				
//				user.setFlatID(wechatToken.getOpenid());
				user.setUserName(wechatToken.get("openid").toString());
				user.setCreationDate(new Date());
				user.setActivated(1);
				
				userService.itripCreateUser(user);				
			}
			String token = tokenService.generateToken(
					request.getHeader("user-agent"), user);
			tokenService.save(token, user);
			/*
			response.setHeader("token", token);
			response.setHeader("access_token", wechatToken.get("access_token").toString());
			response.setHeader("expires_in", wechatToken.get("expires_in").toString());
			response.setHeader("refresh_token", wechatToken.get("refresh_token").toString());
			response.setHeader("openid", wechatToken.get("openid").toString());
			*/
			
			
			//返回前端处理
			StringBuilder loginPage=new StringBuilder();
			loginPage.append("http://itrip.project.bdqn.cn/#/login");
			loginPage.append("?user_type=1&token="+token);
			loginPage.append("&access_token="+wechatToken.get("access_token").toString());
			loginPage.append("&expires_in="+wechatToken.get("expires_in").toString());
			loginPage.append("&refresh_token="+wechatToken.get("refresh_token").toString());
			loginPage.append("&openid="+wechatToken.get("openid").toString());			
			response.sendRedirect(loginPage.toString());
			
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
	}
	/**
	 * 获取微信用户信息
	 * @param accessToken 微信会话凭据
	 * @param openid 微信用户唯一标识
	 * @return
	 */
	@ApiOperation(value = "获取微信用户信息", httpMethod = "GET", 
			protocols = "HTTP", produces = "application/json", 
			response = Dto.class, 
			notes = "微信登录后，提供客户端获取用户信息操作")	
	@ApiImplicitParams(value = { 
		@ApiImplicitParam(paramType="query",required=true,name="accessToken",value="微信会话凭据"),			
		@ApiImplicitParam(paramType="query",required=true,name="openid",value="微信用户唯一标识")
	})
	@RequestMapping(value = "/wechat/user/info", method = RequestMethod.GET,produces= "application/json")
	public @ResponseBody Dto wechatUserInfo(
			@RequestParam String accessToken,
			@RequestParam String openid){
		try {
			//加载用户信息
			String userInfoJson=UrlUtils.loadURL("https://api.weixin.qq.com/sns/userinfo?access_token="
					+accessToken
					+"&openid="+ openid
					);
			Map<String,Object> userInfo=JSON.parseObject(userInfoJson, Map.class);
			/*
			for(Entry<String, Object> entry :userInfo.entrySet()){
				System.out.println(entry.getKey()+":"+entry.getValue().toString());
			}
			*/
			
			return DtoUtil.returnDataSuccess(userInfo);
		} catch (Exception e) {			
			e.printStackTrace();
			return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_UNKNOWN);
		}
	}
	/**
	 * 会话保持
	 * 1、保持和itrip的会话
	 * 2、保持和wechat的会话
	 * @return
	 */

	@ApiOperation(value = "微信登录时客户端置换token", httpMethod = "POST", 
					protocols = "HTTP", produces = "application/json", 
					response = Dto.class, 
					notes = "提供客户端置换token操作，服务器需要获取客户端header中的token串、refresh_token")	
	@ApiImplicitParams(value = { 
			@ApiImplicitParam(paramType="header",required=true,name="token",value="用户认证凭据",defaultValue="token:PC-65e59e2bc836a297575c2e0cc191304f-38-20170725085123-ba081c"),			
			@ApiImplicitParam(paramType="header",required=true,name="refreshtoken",value="用户微信刷新会话的凭据",defaultValue="809r0G5dkJLqnmFfopnf3ZIgFAN3qv30NVN4jHsnI6Bz6XQ3p6rllVl81_-GrJDsVhMOa69Fic4DWbVByq-8ix0olO_BFPGDuFuJdB8l0kU")
	})
	@RequestMapping(value = "/wechat/token/refresh", method = RequestMethod.POST,produces= "application/json")
	public @ResponseBody Dto wechatRefreshToken(HttpServletRequest request,HttpServletResponse response){
				
		String agent=request.getHeader("user-agent");
		String token=request.getHeader("token");		
		String refreshToken=request.getHeader("refreshtoken");//此处header中的名称使用“_”不能获取
		
		try {
			//1、保持和itrip的会话
			String newToken=tokenService.replaceToken(agent, token);
			//2、保持和wechat的会话
			String refreshTokenUrl="https://api.weixin.qq.com/sns/oauth2/refresh_token?appid=wx9168f76f000a0d4c&grant_type=refresh_token&refresh_token="+refreshToken.trim();
			
			String json=UrlUtils.loadURL(refreshTokenUrl);			
			Map<String,Object> wechatToken=JSON.parseObject(json, Map.class);
			if(null!=wechatToken.get("errcode")){
				return DtoUtil.returnFail(wechatToken.get("errmsg").toString(), ErrorCode.AUTH_REPLACEMENT_FAILED);
			}
				
			//返回ItripWechatTokenVO (整合了本地会话与微信会话)
			ItripWechatTokenVO wechatTokenVO=new ItripWechatTokenVO(newToken,
					Calendar.getInstance().getTimeInMillis()+TokenService.SESSION_TIMEOUT*1000,//2h有效期
					Calendar.getInstance().getTimeInMillis());
			wechatTokenVO.setAccessToken(wechatToken.get("access_token").toString());
			wechatTokenVO.setExpiresIn(wechatToken.get("expires_in").toString());
			wechatTokenVO.setRefreshToken(wechatToken.get("refresh_token").toString());
			wechatTokenVO.setOpenid(wechatToken.get("openid").toString());		
			return DtoUtil.returnDataSuccess(wechatTokenVO);
			
		} catch (Exception e) {
			e.printStackTrace();
			return DtoUtil.returnFail(e.getMessage(), ErrorCode.AUTH_REPLACEMENT_FAILED);
		}	
		
	}
	
	
	
	
}

