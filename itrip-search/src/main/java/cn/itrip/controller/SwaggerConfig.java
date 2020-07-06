package cn.itrip.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

//@
@EnableSwagger2
@ComponentScan(basePackages = {"cn.itrip.controller"})
@Configuration
public class SwaggerConfig extends WebMvcConfigurationSupport {

	//类似于去获取controller接口的信息
	@Bean
	public Docket createRestApi() {
		return new Docket(DocumentationType.SWAGGER_2)
				.apiInfo(apiInfo())
				.select()
				.apis(RequestHandlerSelectors.any())
				.paths(PathSelectors.any())
				.build();
	}
	//去定制生成文档后的表头
	private ApiInfo apiInfo() {
		return new ApiInfoBuilder()
				.title("爱旅行-搜索模块API")
				.termsOfServiceUrl("http://www.itrip.com/search")
				.contact("爱旅行")
				.version("1.0")
				.build();
	}
}