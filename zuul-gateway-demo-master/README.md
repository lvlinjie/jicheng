# zuul-gateway-demo
zuul动态路由demo

前言
--

Zuul 是Netflix 提供的一个开源组件,致力于在云平台上提供动态路由，监控，弹性，安全等边缘服务的框架。也有很多公司使用它来作为网关的重要组成部分，碰巧今年公司的架构组决定自研一个网关产品，集动态路由，动态权限，限流配额等功能为一体，为其他部门的项目提供统一的外网调用管理，最终形成产品(这方面阿里其实已经有成熟的网关产品了，但是不太适用于个性化的配置，也没有集成权限和限流降级)。

不过这里并不想介绍整个网关的架构，而是想着重于讨论其中的一个关键点，并且也是经常在交流群中听人说起的：动态路由怎么做？

再阐释什么是动态路由之前，需要介绍一下架构的设计。

传统互联网架构图
---------

![这里写图片描述](http://img.blog.csdn.net/20170401101904656?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvdTAxMzgxNTU0Ng==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)
上图是没有网关参与的一个最典型的互联网架构(本文中统一使用book代表应用实例，即真正提供服务的一个业务系统)

加入eureka的架构图
-------------

![这里写图片描述](http://img.blog.csdn.net/20170401103146894?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvdTAxMzgxNTU0Ng==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)
book注册到eureka注册中心中，zuul本身也连接着同一个eureka，可以拉取book众多实例的列表。服务中心的注册发现一直是值得推崇的一种方式，但是不适用与网关产品。因为我们的网关是面向众多的**其他部门**的**已有**或是**异构架构**的系统，不应该强求其他系统都使用eureka，这样是有侵入性的设计。
```
zuul.routes.books.url=http://localhost:8090
zuul.routes.books.path=/book/**
```
我们先看一下ZuulProperties 的zuul配置类,从中我们可以看到一个属性:
```
private Map<String, ZuulProperties.ZuulRoute> routes = new LinkedHashMap();
```
从名字上看 知道是路由集合,而且有对应的set方法,经过对配置元数据json的解读,确认这就是装载路由的容器,我们可以在 ZuulProperties  初始化的时候 将路由装载到容器中,那么 ZuulRoute 又是个什么玩意儿呢：

```
public static class ZuulRoute {
        private String id;//路由的唯一编号  同时也默认为 装载路由的容器的Key 用来标识映射的唯一性 重要
        private String path;//路由的规则 /book/**
        private String serviceId;//服务实例ID（如果有的话）来映射到此路由 你可以指定一个服务或者url 但是不能两者同时对于一个key来配置
        private String url;//就是上面提到的url
        /**
        *路由前缀是否在转发开始前被删除 默认是删除,举个例子 你实例的实际调用是http://localhost:8002/user/info
        *如果你路由设置该实例对应的path 为 /api/v1/**  那么 通过路由调用 
        *http://ip:port/api/v1/user/info
        *  当为true 转发到 http://localhost:8002/user/info
        *  当为false 转发到 http://localhost:8002//api/v1user/info 
        */
        private boolean stripPrefix = true;
        private Boolean retryable;//是否支持重试如果支持的话  通常需要服务实例id 跟ribbon
        /**
        *不传递到下游请求的敏感标头列表。默认为“安全”的头集，通常包含用户凭证。如果下游服务是与代理相同的系统的一
        *部分，那么将它们从列表中删除就可以了，因此它们共享身份验证数据。如果在自己的域之外使用物理URL，那么通常来
        *说泄露用户凭证是一个坏主意
        */
        private Set<String> sensitiveHeaders = new LinkedHashSet();
        /**
        *上述列表sensitiveHeaders  是否生效 默认不生效
        */
        private boolean customSensitiveHeaders = false;

       ^^^^^^
    }

```
上面这些就是我们需要进行持久化的东西,你可以用你知道的持久化方式来实现,当然这些可以加入缓存来减少IO提高性能,这里只说一个思路具体自己可以实现


最终架构图
-----

![这里写图片描述](http://img.blog.csdn.net/20170401111650676?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvdTAxMzgxNTU0Ng==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)
要强调的一点是，gateway最终也会部署多个实例，达到分布式的效果，在架构图中没有画出.

 

动态路由
----
动态路由需要达到可持久化配置，动态刷新的效果。如架构图所示，不仅要能满足从spring的配置文件properties加载路由信息，还需要从数据库加载我们的配置。另外一点是，路由信息在容器启动时就已经加载进入了内存，我们希望配置完成后，实施发布，动态刷新内存中的路由信息，达到不停机维护路由信息的效果。



zuul--HelloWorldDemo
--------------------

项目结构
```
	<groupId>com.sinosoft</groupId>
    <artifactId>zuul-gateway-demo</artifactId>
    <packaging>pom</packaging>
    <version>1.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>1.5.2.RELEASE</version>
    </parent>

    <modules>
        <module>gateway</module>
        <module>book</module>
    </modules>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>Camden.SR6</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```
tip：springboot-1.5.2对应的springcloud的版本需要使用Camden.SR6，一开始想专门写这个demo时，只替换了springboot的版本1.4.0->1.5.2，结果启动就报错了，最后发现是版本不兼容的锅。

gateway项目：
启动类：`GatewayApplication.java`
```
@EnableZuulProxy
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}
```
配置：`application.properties`

```
#配置在配置文件中的路由信息
zuul.routes.books.url=http://localhost:8090
zuul.routes.books.path=/books/**
#不使用注册中心,会带来侵入性
ribbon.eureka.enabled=false
#网关端口
server.port=8080
```

book项目：
启动类：`BookApplication.java`

```
@RestController
@SpringBootApplication
public class BookApplication {

    @RequestMapping(value = "/available")
    public String available() {
        System.out.println("Spring in Action");
        return "Spring in Action";
    }

    @RequestMapping(value = "/checked-out")
    public String checkedOut() {
        return "Spring Boot in Action";
    }

    public static void main(String[] args) {
        SpringApplication.run(BookApplication.class, args);
    }
}
```
配置类：`application.properties`

```
server.port=8090
```
测试访问：http://localhost:8080/books/available

当持久化完成后 我们如何让网关来刷新这些配置呢  每一次的curd 能迅速生效呢?
这个就要路由加载的机制和原理:路由是由路由定位器来 ,配置中获取路由表进行匹配的
```$xslt

public interface RouteLocator {
    Collection<String> getIgnoredPaths();
    /**
    *获取路由表
    */
    List<Route> getRoutes();
    /**
    *将路径映射到具有完整元数据的实际路由
    */
    Route getMatchingRoute(String path);
}
```
 实现有这么几个:
 
 ![这里写图片描述](https://static.oschina.net/uploads/space/2017/1219/233803_fEGe_2948566.png)

 第一个：复合定位器 CompositeRouteLocator

```$xslt
public class CompositeRouteLocator implements RefreshableRouteLocator {
	private final Collection<? extends RouteLocator> routeLocators;
	private ArrayList<RouteLocator> rl;

	public CompositeRouteLocator(Collection<? extends RouteLocator> routeLocators) {
		Assert.notNull(routeLocators, "'routeLocators' must not be null");
		rl = new ArrayList<>(routeLocators);
		AnnotationAwareOrderComparator.sort(rl);
		this.routeLocators = rl;
	}

	@Override
	public Collection<String> getIgnoredPaths() {
		List<String> ignoredPaths = new ArrayList<>();
		for (RouteLocator locator : routeLocators) {
			ignoredPaths.addAll(locator.getIgnoredPaths());
		}
		return ignoredPaths;
	}

	@Override
	public List<Route> getRoutes() {
		List<Route> route = new ArrayList<>();
		for (RouteLocator locator : routeLocators) {
			route.addAll(locator.getRoutes());
		}
		return route;
	}

	@Override
	public Route getMatchingRoute(String path) {
		for (RouteLocator locator : routeLocators) {
			Route route = locator.getMatchingRoute(path);
			if (route != null) {
				return route;
			}
		}
		return null;
	}

	@Override
	public void refresh() {
		for (RouteLocator locator : routeLocators) {
			if (locator instanceof RefreshableRouteLocator) {
				((RefreshableRouteLocator) locator).refresh();
			}
		}
	}
}
```


这是一个既可以刷新 又可以定位的定位器,作用:可以将一个到多个定位器转换成可刷新的定位器.
看构造:传入路由定位器集合,然后进行了排序,赋值 ,同时实现了路由定位器的方法跟刷新方法

我们刷新 可以根据将定位器  放入这个容器进行转换   
第二个  DiscoveryClientRouteLocator    是组合  静态  以及配置好的路由 跟一个服务发现实例  而且有优先权

第三个 RefreshableRouteLocator   实现 即可实现  动态刷新逻辑

第四个 SimpleRouteLocator    可以发现 第二个 继承了此定位器  说明 这个是一个基础的实现   基于所有的配置

我们可以从   第一跟第四个下功夫

将配置从DB读取 放入 SimpleRouteLocator     再注入到CompositeRouteLocator.
刷新的核心类 ：


```$xslt
package org.springframework.cloud.netflix.zuul;

import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.context.ApplicationEvent;

/**
 * @author Dave Syer
 */
@SuppressWarnings("serial")
public class RoutesRefreshedEvent extends ApplicationEvent {

	private RouteLocator locator;

	public RoutesRefreshedEvent(RouteLocator locator) {
		super(locator);
		this.locator = locator;
	}

	public RouteLocator getLocator() {
		return this.locator;
	}

}
```
基于 事件    我们只要写一个监听器 来监听 就OK了  具体  自行实现



上述demo是一个简单的**静态路由**，简单看下源码，zuul是怎么做到转发，路由的。


```
@Configuration
@EnableConfigurationProperties({ ZuulProperties.class })
@ConditionalOnClass(ZuulServlet.class)
@Import(ServerPropertiesAutoConfiguration.class)
public class ZuulConfiguration {
	@Autowired
	//zuul的配置文件,对应了application.properties中的配置信息
	protected ZuulProperties zuulProperties;
	@Autowired
	protected ServerProperties server;
	@Autowired(required = false)
	private ErrorController errorController;
	@Bean
	public HasFeatures zuulFeature() {
		return HasFeatures.namedFeature("Zuul (Simple)", ZuulConfiguration.class);
	}
	//核心类，路由定位器，最最重要,这里是用默认的SimpleRouteLocator定位器来加载路由信息的
	@Bean
	@ConditionalOnMissingBean(RouteLocator.class)
	public RouteLocator routeLocator() {
		//默认配置的实现是SimpleRouteLocator.class
		return new SimpleRouteLocator(this.server.getServletPrefix(),
				this.zuulProperties);
	}
	//zuul的控制器，负责处理链路调用
	@Bean
	public ZuulController zuulController() {
		return new ZuulController();
	}
	//MVC HandlerMapping that maps incoming request paths to remote services.
	@Bean
	public ZuulHandlerMapping zuulHandlerMapping(RouteLocator routes) {
		ZuulHandlerMapping mapping = new ZuulHandlerMapping(routes, zuulController());
		mapping.setErrorController(this.errorController);
		return mapping;
	}
	//注册了一个路由刷新监听器，默认实现是ZuulRefreshListener.class，这个是我们动态路由的关键
	@Bean
	public ApplicationListener<ApplicationEvent> zuulRefreshRoutesListener() {
		return new ZuulRefreshListener();
	}
	@Bean
	@ConditionalOnMissingBean(name = "zuulServlet")
	public ServletRegistrationBean zuulServlet() {
		ServletRegistrationBean servlet = new ServletRegistrationBean(new ZuulServlet(),
				this.zuulProperties.getServletPattern());
		// The whole point of exposing this servlet is to provide a route that doesn't
		// buffer requests.
		servlet.addInitParameter("buffer-requests", "false");
		return servlet;
	}
	// pre filters
	@Bean
	public ServletDetectionFilter servletDetectionFilter() {
		return new ServletDetectionFilter();
	}
	@Bean
	public FormBodyWrapperFilter formBodyWrapperFilter() {
		return new FormBodyWrapperFilter();
	}
	@Bean
	public DebugFilter debugFilter() {
		return new DebugFilter();
	}
	@Bean
	public Servlet30WrapperFilter servlet30WrapperFilter() {
		return new Servlet30WrapperFilter();
	}
	// post filters
	@Bean
	public SendResponseFilter sendResponseFilter() {
		return new SendResponseFilter();
	}
	@Bean
	public SendErrorFilter sendErrorFilter() {
		return new SendErrorFilter();
	}
	@Bean
	public SendForwardFilter sendForwardFilter() {
		return new SendForwardFilter();
	}
	@Configuration
	protected static class ZuulFilterConfiguration {
		@Autowired
		private Map<String, ZuulFilter> filters;
		@Bean
		public ZuulFilterInitializer zuulFilterInitializer() {
			return new ZuulFilterInitializer(this.filters);
		}
	}
	//上面提到的路由刷新监听器
	private static class ZuulRefreshListener
			implements ApplicationListener<ApplicationEvent> {
		@Autowired
		private ZuulHandlerMapping zuulHandlerMapping;
		private HeartbeatMonitor heartbeatMonitor = new HeartbeatMonitor();
		@Override
		public void onApplicationEvent(ApplicationEvent event) {
			if (event instanceof ContextRefreshedEvent
					|| event instanceof RefreshScopeRefreshedEvent
					|| event instanceof RoutesRefreshedEvent) {
				//设置为脏,下一次匹配到路径时，如果发现为脏，则会去刷新路由信息
				this.zuulHandlerMapping.setDirty(true);
			}
			else if (event instanceof HeartbeatEvent) {
				if (this.heartbeatMonitor.update(((HeartbeatEvent) event).getValue())) {
					this.zuulHandlerMapping.setDirty(true);
				}
			}
		}

	}

}
```
我们要解决动态路由的难题，第一步就得理解路由定位器的作用。
![这里写图片描述](http://img.blog.csdn.net/20170401115214231?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvdTAxMzgxNTU0Ng==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)
很失望，因为从接口关系来看，spring考虑到了路由刷新的需求，但是默认实现的SimpleRouteLocator没有实现RefreshableRouteLocator接口，看来我们只能借鉴DiscoveryClientRouteLocator去改造SimpleRouteLocator使其具备刷新能力。
```
public interface RefreshableRouteLocator extends RouteLocator {
	void refresh();
}
```
DiscoveryClientRouteLocator比SimpleRouteLocator多了两个功能，第一是从DiscoveryClient（如Eureka）发现路由信息，之前的架构图已经给大家解释清楚了，我们不想使用eureka这种侵入式的网关模块，所以忽略它，第二是实现了RefreshableRouteLocator接口，能够实现动态刷新。
对SimpleRouteLocator.class的源码加一些注释，方便大家阅读：

```
public class SimpleRouteLocator implements RouteLocator {

	//配置文件中的路由信息配置
	private ZuulProperties properties;
	//路径正则配置器,即作用于path:/books/**
	private PathMatcher pathMatcher = new AntPathMatcher();

	private String dispatcherServletPath = "/";
	private String zuulServletPath;

	private AtomicReference<Map<String, ZuulRoute>> routes = new AtomicReference<>();

	public SimpleRouteLocator(String servletPath, ZuulProperties properties) {
		this.properties = properties;
		if (servletPath != null && StringUtils.hasText(servletPath)) {
			this.dispatcherServletPath = servletPath;
		}

		this.zuulServletPath = properties.getServletPath();
	}

	//路由定位器和其他组件的交互，是最终把定位的Routes以list的方式提供出去,核心实现
	@Override
	public List<Route> getRoutes() {
		if (this.routes.get() == null) {
			this.routes.set(locateRoutes());
		}
		List<Route> values = new ArrayList<>();
		for (String url : this.routes.get().keySet()) {
			ZuulRoute route = this.routes.get().get(url);
			String path = route.getPath();
			values.add(getRoute(route, path));
		}
		return values;
	}

	@Override
	public Collection<String> getIgnoredPaths() {
		return this.properties.getIgnoredPatterns();
	}

	//这个方法在网关产品中也很重要，可以根据实际路径匹配到Route来进行业务逻辑的操作，进行一些加工
	@Override
	public Route getMatchingRoute(final String path) {

		if (log.isDebugEnabled()) {
			log.debug("Finding route for path: " + path);
		}

		if (this.routes.get() == null) {
			this.routes.set(locateRoutes());
		}

		if (log.isDebugEnabled()) {
			log.debug("servletPath=" + this.dispatcherServletPath);
			log.debug("zuulServletPath=" + this.zuulServletPath);
			log.debug("RequestUtils.isDispatcherServletRequest()="
					+ RequestUtils.isDispatcherServletRequest());
			log.debug("RequestUtils.isZuulServletRequest()="
					+ RequestUtils.isZuulServletRequest());
		}

		String adjustedPath = adjustPath(path);

		ZuulRoute route = null;
		if (!matchesIgnoredPatterns(adjustedPath)) {
			for (Entry<String, ZuulRoute> entry : this.routes.get().entrySet()) {
				String pattern = entry.getKey();
				log.debug("Matching pattern:" + pattern);
				if (this.pathMatcher.match(pattern, adjustedPath)) {
					route = entry.getValue();
					break;
				}
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("route matched=" + route);
		}

		return getRoute(route, adjustedPath);

	}

	private Route getRoute(ZuulRoute route, String path) {
		if (route == null) {
			return null;
		}
		String targetPath = path;
		String prefix = this.properties.getPrefix();
		if (path.startsWith(prefix) && this.properties.isStripPrefix()) {
			targetPath = path.substring(prefix.length());
		}
		if (route.isStripPrefix()) {
			int index = route.getPath().indexOf("*") - 1;
			if (index > 0) {
				String routePrefix = route.getPath().substring(0, index);
				targetPath = targetPath.replaceFirst(routePrefix, "");
				prefix = prefix + routePrefix;
			}
		}
		Boolean retryable = this.properties.getRetryable();
		if (route.getRetryable() != null) {
			retryable = route.getRetryable();
		}
		return new Route(route.getId(), targetPath, route.getLocation(), prefix,
				retryable,
				route.isCustomSensitiveHeaders() ? route.getSensitiveHeaders() : null);
	}

	//注意这个类并没有实现refresh接口，但是却提供了一个protected级别的方法,旨在让子类不需要重复维护一个private AtomicReference<Map<String, ZuulRoute>> routes = new AtomicReference<>();也可以达到刷新的效果
	protected void doRefresh() {
		this.routes.set(locateRoutes());
	}


	//具体就是在这儿定位路由信息的，我们之后从数据库加载路由信息，主要也是从这儿改写
	/**
	 * Compute a map of path pattern to route. The default is just a static map from the
	 * {@link ZuulProperties}, but subclasses can add dynamic calculations.
	 */
	protected Map<String, ZuulRoute> locateRoutes() {
		LinkedHashMap<String, ZuulRoute> routesMap = new LinkedHashMap<String, ZuulRoute>();
		for (ZuulRoute route : this.properties.getRoutes().values()) {
			routesMap.put(route.getPath(), route);
		}
		return routesMap;
	}

	protected boolean matchesIgnoredPatterns(String path) {
		for (String pattern : this.properties.getIgnoredPatterns()) {
			log.debug("Matching ignored pattern:" + pattern);
			if (this.pathMatcher.match(pattern, path)) {
				log.debug("Path " + path + " matches ignored pattern " + pattern);
				return true;
			}
		}
		return false;
	}

	private String adjustPath(final String path) {
		String adjustedPath = path;

		if (RequestUtils.isDispatcherServletRequest()
				&& StringUtils.hasText(this.dispatcherServletPath)) {
			if (!this.dispatcherServletPath.equals("/")) {
				adjustedPath = path.substring(this.dispatcherServletPath.length());
				log.debug("Stripped dispatcherServletPath");
			}
		}
		else if (RequestUtils.isZuulServletRequest()) {
			if (StringUtils.hasText(this.zuulServletPath)
					&& !this.zuulServletPath.equals("/")) {
				adjustedPath = path.substring(this.zuulServletPath.length());
				log.debug("Stripped zuulServletPath");
			}
		}
		else {
			// do nothing
		}

		log.debug("adjustedPath=" + path);
		return adjustedPath;
	}

}
```
重写过后的自定义路由定位器如下：

```
public class CustomRouteLocator extends SimpleRouteLocator implements RefreshableRouteLocator{

    public final static Logger logger = LoggerFactory.getLogger(CustomRouteLocator.class);

    private JdbcTemplate jdbcTemplate;

    private ZuulProperties properties;

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomRouteLocator(String servletPath, ZuulProperties properties) {
        super(servletPath, properties);
        this.properties = properties;
        logger.info("servletPath:{}",servletPath);
    }

    //父类已经提供了这个方法，这里写出来只是为了说明这一个方法很重要！！！
//    @Override
//    protected void doRefresh() {
//        super.doRefresh();
//    }


    @Override
    public void refresh() {
        doRefresh();
    }

    @Override
    protected Map<String, ZuulRoute> locateRoutes() {
        LinkedHashMap<String, ZuulRoute> routesMap = new LinkedHashMap<String, ZuulRoute>();
        //从application.properties中加载路由信息
        routesMap.putAll(super.locateRoutes());
        //从db中加载路由信息
        routesMap.putAll(locateRoutesFromDB());
        //优化一下配置
        LinkedHashMap<String, ZuulRoute> values = new LinkedHashMap<>();
        for (Map.Entry<String, ZuulRoute> entry : routesMap.entrySet()) {
            String path = entry.getKey();
            // Prepend with slash if not already present.
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (StringUtils.hasText(this.properties.getPrefix())) {
                path = this.properties.getPrefix() + path;
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
            }
            values.put(path, entry.getValue());
        }
        return values;
    }

    private Map<String, ZuulRoute> locateRoutesFromDB(){
        Map<String, ZuulRoute> routes = new LinkedHashMap<>();
        List<ZuulRouteVO> results = jdbcTemplate.query("select * from gateway_api_define where enabled = true ",new BeanPropertyRowMapper<>(ZuulRouteVO.class));
        for (ZuulRouteVO result : results) {
            if(org.apache.commons.lang3.StringUtils.isBlank(result.getPath()) || org.apache.commons.lang3.StringUtils.isBlank(result.getUrl()) ){
                continue;
            }
            ZuulRoute zuulRoute = new ZuulRoute();
            try {
                org.springframework.beans.BeanUtils.copyProperties(result,zuulRoute);
            } catch (Exception e) {
                logger.error("=============load zuul route info from db with error==============",e);
            }
            routes.put(zuulRoute.getPath(),zuulRoute);
        }
        return routes;
    }

    public static class ZuulRouteVO {

        /**
         * The ID of the route (the same as its map key by default).
         */
        private String id;

        /**
         * The path (pattern) for the route, e.g. /foo/**.
         */
        private String path;

        /**
         * The service ID (if any) to map to this route. You can specify a physical URL or
         * a service, but not both.
         */
        private String serviceId;

        /**
         * A full physical URL to map to the route. An alternative is to use a service ID
         * and service discovery to find the physical address.
         */
        private String url;

        /**
         * Flag to determine whether the prefix for this route (the path, minus pattern
         * patcher) should be stripped before forwarding.
         */
        private boolean stripPrefix = true;

        /**
         * Flag to indicate that this route should be retryable (if supported). Generally
         * retry requires a service ID and ribbon.
         */
        private Boolean retryable;

        private Boolean enabled;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isStripPrefix() {
            return stripPrefix;
        }

        public void setStripPrefix(boolean stripPrefix) {
            this.stripPrefix = stripPrefix;
        }

        public Boolean getRetryable() {
            return retryable;
        }

        public void setRetryable(Boolean retryable) {
            this.retryable = retryable;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
```
配置这个自定义的路由定位器：

```
@Configuration
public class CustomZuulConfig {

    @Autowired
    ZuulProperties zuulProperties;
    @Autowired
    ServerProperties server;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Bean
    public CustomRouteLocator routeLocator() {
        CustomRouteLocator routeLocator = new CustomRouteLocator(this.server.getServletPrefix(), this.zuulProperties);
        routeLocator.setJdbcTemplate(jdbcTemplate);
        return routeLocator;
    }

}
```
现在容器启动时，就可以从数据库和配置文件中一起加载路由信息了，离动态路由还差最后一步，就是实时刷新，前面已经说过了，默认的ZuulConfigure已经配置了事件监听器，我们只需要发送一个事件就可以实现刷新了。

```
public class RefreshRouteService {

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    RouteLocator routeLocator;

    public void refreshRoute() {
        RoutesRefreshedEvent routesRefreshedEvent = new RoutesRefreshedEvent(routeLocator);
        publisher.publishEvent(routesRefreshedEvent);
    }

}
```
具体的刷新流程其实就是从数据库重新加载了一遍，有人可能会问，为什么不自己是手动重新加载Locator.dorefresh？非要用事件去刷新。这牵扯到内部的zuul内部组件的工作流程，不仅仅是Locator本身的一个变量，具体想要了解的还得去看源码。
