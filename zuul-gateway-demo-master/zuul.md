
> 当我们在使用微服务的时候，完成一个业务可能需要同时调用多个微服务，则需要分别请求多个服务接口，首先客户端需要请求不同的微服务，客户端的复杂性增大认证复杂，每个微服务都需要自己独立的认证方式，某些情况下会存在一些问题，例如跨域问题，每个服务存在的协议可能不同，这里借助一位博友的图片方便各位更好理解zuul入门
 
 ![](.zuul_images/5aeab762.png)
 
 
 ![](.zuul_images/bb9d1338.png)
 
 
> 通常情况下假如业务需求,需要分别去调用用户服务，产品服务和订单服务三个独立的微服务，为了这种情况Spring-Cloud提供了zuul服务作为网关层,利用zuul服务我们只需要访问网关层，zuul即会通过特定的路由规则将请求自动转发到指定的微服务上，zuul本身也是一个服务,和其他api服务一样共同注册到eureka上,可以相互发现。
 通过zuul我们也可以做很多公共的预处理,例如权限认证、token校验、压力测试等。
 
### 1. 什么是Zuul

Zuul是Netflix开源的微服务网关，他可以和Eureka,Ribbon,Hystrix等组件配合使用。Zuul组件的核心是一系列的过滤器，这些过滤器可以完成以下功能：
1. 身份认证和安全:识别每一个资源的验证要求 审查和监控
2. 动态路由：动态将请求路由到不同后端集群
3. 压力测试：组建增加指定集群流量，检测性能
4. 负载分配：为不同负载类型分配对应容量
5. 静态响应处理：边缘位置响应，避免转发到内部集群
6. 多区域弹性

Spring Cloud对Zuul进行了整合和增强，Zuul使用的默认是Apache的HTTP Client，也可以使用Rest Client可以设置ribbon.restclient.enabled=true，Zuul 在云平台上提供动态路由，监控，弹性，安全等边缘服务的框架。Zuul 相当于是设备和 Netflix 流应用的 Web网站后端所有请求的前门。

![](.zuul_images/7d2356b9.png)

### 2. Zuul简单应用
构建项目，引入需要依赖
pom.xml
```
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-zuul</artifactId>
    <version>1.4.3.RELEASE</version>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    <version>1.4.3.RELEASE</version>
</dependency>
<!--整合达到安全控制-->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

```
application.yml

```
server:
  port: 10900 #程序启动端口,tomcat端口(可自定义)
spring:
  application:
    name: api-gateway-zuul #应用名称(别名)
eureka:
  client:
    serviceUrl:
      defaultZone: http://user:user@localhost:8888/eureka
  instance:
    prefer-ip-address: true #显示 ip
security:  #配置访问 zuul 的 routes 的时候使用
  user:
    name: zuul
    password: zuul

```
ZuulApp.java

```
package com.ithzk.spring.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@EnableZuulProxy//启用 zuul,自带熔断
public class ZuulApp
{
    public static void main( String[] args )
    {
        SpringApplication.run(ZuulApp.class);
    }
}

```
启动之前的Eureka Server以及provider-user服务,访问localhost:8888 Eureka Server。
![](.zuul_images/e62fc8ef.png)

可以发现Zuul服务已经被注册上去,通常情况下我们调用提供服务http://localhost:7900/user/55即可，这里通过Zuul去调用,只需调用http://192.168.126.1:10900/provider-user/user/55，此处zuul后面跟上服务注册名称以及调用的服务接口和参数即可，这里需要利用我们zuul服务配置的用户名和密码。
![](.zuul_images/3db5e96d.png)
![](.zuul_images/73706608.png)

可以看出来我们成功通过zuul服务调用到了我们所需的服务得到了想要的结果

### 3. 自定义服务映射地址
> 上面我们简单去利用zuul完成调用特定服务,但是我们发现当我们服务注册很多并且名称很长没有规律时,可能调用会变得十分复杂，zuul提供了自定义服务映射地址的功能，我们只需修改配置就能实现。

+ 服务名称映射
 application.yml
 ```
 server:
   port: 10900 #程序启动端口,tomcat端口(可自定义)
 spring:
   application:
     name: api-gateway-zuul #应用名称(别名)
 eureka:
   client:
     serviceUrl:
       defaultZone: http://user:user@localhost:8888/eureka
   instance:
     prefer-ip-address: true #显示 ip
 security:  #配置访问 zuul 的 routes 的时候使用
   user:
     name: zuul
     password: zuul
 zuul:
   routes: #配置路由映射
     provider-user: /route-map1/** #给指定的服务做映射, 当前配置给provider-user添加映射地址为/route-map1
```
通过zuul.routes.applicationName就可以给指定的服务添加地址映射,配置成功后我们调用http://192.168.126.1:10900/route-map1/user/88可以发现和之前达到同样的效果。我们通过访问http://192.168.126.1:10900/routes可以查看到当前zuul映射的路由地址对应关系
 
 ![](.zuul_images/eed46528.png)
 
 + path绑定映射
 > 里再介绍另外一种利用path属性自定义映射地址的方式,同样只需修改配置文件即可
 
 application.yml
 
 ```
 server:
   port: 10900 #程序启动端口,tomcat端口(可自定义)
 spring:
   application:
     name: api-gateway-zuul #应用名称(别名)
 eureka:
   client:
     serviceUrl:
       defaultZone: http://user:user@localhost:8888/eureka
   instance:
     prefer-ip-address: true #显示 ip
 security:  #配置访问 zuul 的 routes 的时候使用
   user:
     name: zuul
     password: zuul
 zuul:
   routes: #配置路由映射
     route-map2: #保证唯一即可
       path: /route-map2/** #映射的路径
       serviceId: provider-user #给特定服务做映射,和之前效果一样
```
通过这种方式可以和上面达成一样的效果，前者是通过服务名称去映射地址，后者是通过映射地址去绑定服务名称，查看zuul服务路由：

![](.zuul_images/bd3204ab.png)

可以看出zuul绑定关系已经修改，通过新的映射地址访问同样可以获得预期效果

*  url绑定映射
> 上面通过serviceId去绑定指定服务名称服务，还可以通过url属性直接绑定对应url

application.yml
```server:
     port: 10900 #程序启动端口,tomcat端口(可自定义)
   spring:
     application:
       name: api-gateway-zuul #应用名称(别名)
   eureka:
     client:
       serviceUrl:
         defaultZone: http://user:user@localhost:8888/eureka
     instance:
       prefer-ip-address: true #显示 ip
   security:  #配置访问 zuul 的 routes 的时候使用
     user:
       name: zuul
       password: zuul
   zuul:
     routes: #配置路由映射
       route-map2: #保证唯一即可
         path: /route-map2/** #映射的路径
         #serviceId: provider-user #给特定服务做映射,和之前效果一样
         url: http://localhost:7900/
```
![](.zuul_images/1ea11c6f.png)

效果也完全一样，这三种方式都可以指定服务映射地址,需要注意的是这些简单的URL路由不会被执行为HystrixCommand也不能使用Ribbon对多个URL进行负载均衡。

---

 * url绑定映射实现负载均衡
 
 > 若我们想要达到负载均衡的效果,需要使用serviceId配置的方式再加上listOfServers配置
 
 application.yml
 
 ```
 server:
   port: 10900 #程序启动端口,tomcat端口(可自定义)
 spring:
   application:
     name: api-gateway-zuul #应用名称(别名)
 eureka:
   client:
     serviceUrl:
       defaultZone: http://user:user@localhost:8888/eureka
   instance:
     prefer-ip-address: true #显示 ip
 security:  #配置访问 zuul 的 routes 的时候使用
   user:
     name: zuul
     password: zuul
 zuul:
   routes: #配置路由映射
     route-map2: #保证唯一即可
       path: /route-map2/** #映射的路径
       serviceId: provider-user #给特定服务做映射,和之前效果一样
 ribbon:
   eureka:
     enabled: false #在eureka中禁用 ribbon 的负载均衡
 provider-user: #给配置serviceId对应的服务指定ribbon负载均衡,从ListOfServers配置的地址中选择,多个逗号分隔
   ribbon:
     listOfServers: http://localhost:7900/,http://localhost:7901/
```
启动两个服务,分别端口为7900、7901，通过zuul调用，和之前配置ribbon负载均衡效果一样，默认轮询

![](.zuul_images/20d7d179.png)

* 正则表达式映射

> zuul提供了regexmapper和serviceId的规则绑定,若serviceId符合配置的表达式则会被映射到特定路由模式上，主程序里面添加一段官方提供的代码，正则表达式可以自定义，这里使用官方示例。

ZuulApp.java
```package com.ithzk.spring.cloud;
   
   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;
   import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
   import org.springframework.cloud.netflix.zuul.filters.discovery.PatternServiceRouteMapper;
   import org.springframework.context.annotation.Bean;
   
   /**
    * Hello world!
    *
    */
   @SpringBootApplication
   @EnableZuulProxy//启用 zuul,自带熔断
   public class ZuulApp
   {
       public static void main( String[] args )
       {
           SpringApplication.run(ZuulApp.class);
       }
       
       /**
        * 解析特定匹配路由
        * @return
        */
       @Bean
       public PatternServiceRouteMapper serviceRouteMapper() {
           return new PatternServiceRouteMapper(
                   "(?<name>^.+)-(?<version>v.+$)",
                   "${version}/${name}");
       }
   }
   
```

这里我们启动一个提供服务，修改服务名称为如下格式

application.yml

```server:
     port: 7902 #程序启动后的端口，也就是tomcat的端口，可自定义
   spring:
    application:
      name: provider-user-v1
   eureka:
     client:
       service-url:
         defaultZone:  http://user:user@localhost:8888/eureka/
```
启动后访问http://192.168.126.1:10900/routes,发现该服务被解析成了指定模式路由,调用达到效果

![](.zuul_images/a9ba6b0a.png)

![](.zuul_images/5edc2b12.png)

*  添加前缀映射

> zuul还可以为所有映射添加前缀,只需增加配置zuul.prefix,我们还可以通过zuul.stripPrefix=false去关闭添加前缀的行为，但是关闭的前缀只能是zuul.prefix设置的前缀,官方文档写的还是挺详细的。

application.yml

```server:
     port: 10900 #程序启动端口,tomcat端口(可自定义)
   spring:
     application:
       name: api-gateway-zuul #应用名称(别名)
   eureka:
     client:
       serviceUrl:
         defaultZone: http://user:user@localhost:8888/eureka
     instance:
       prefer-ip-address: true #显示 ip
   security:  #配置访问 zuul 的 routes 的时候使用
     user:
       name: zuul
       password: zuul
   zuul:
     prefix: /prefix-name
```
![](.zuul_images/b0f10224.png)

![](.zuul_images/65da5cfb.png)


