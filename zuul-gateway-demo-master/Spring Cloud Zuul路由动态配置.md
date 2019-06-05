## Zuul配置
一般的，我们如果使用Spring Cloud Zuul 进行路由配置，类似于下面的样子:
```
zuul:
  routes:
    users:
      path: /myusers/**
      stripPrefix: false
```
当我们要新增或者改变一个网关路由时，我们不得不停止网关服务，修改配置文件，保存再重新启动网关服务，这样才能让我们新的设置生效。设想一样，如果是在生产环境，为了一个小小的路由变更，这样的停止再重启恐怕谁也受不了吧。接下来，看看我们怎么能做到动态配置网关路由，让网关路由配置在服务不需要重启的情况生效。（废话一堆啊）

## 在mysql中创建路由信息表，对于类如下：

```
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

```

## 定义CustomRouteLocator类
CustomRouteLocator集成SimpleRouteLocator,实现了RefreshableRouteLocator接口
```
public class CustomRouteLocator extends SimpleRouteLocator implements RefreshableRouteLocator {

    public final static Logger logger = LoggerFactory.getLogger(CustomRouteLocator.class);

    private JdbcTemplate jdbcTemplate;

    private ZuulProperties properties;

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomRouteLocator(String servletPath, ZuulProperties properties) {

        super(servletPath, properties);
        this.properties = properties;
        System.out.println(properties.toString());
        logger.info("servletPath:{}", servletPath);
    }

    @Override
    public void refresh() {
        doRefresh();
    }

    @Override
    protected Map<String, ZuulProperties.ZuulRoute> locateRoutes() {
        LinkedHashMap<String, ZuulProperties.ZuulRoute> routesMap = new LinkedHashMap<>();
        System.out.println("start " + new Date().toLocaleString());
        //从application.properties中加载路由信息
        routesMap.putAll(super.locateRoutes());
        //从db中加载路由信息
        routesMap.putAll(locateRoutesFromDB());
        //优化一下配置
        LinkedHashMap<String, ZuulProperties.ZuulRoute> values = new LinkedHashMap<>();
        for (Map.Entry<String, ZuulProperties.ZuulRoute> entry : routesMap.entrySet()) {
            String path = entry.getKey();
            System.out.println(path);
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

    private Map<String, ZuulProperties.ZuulRoute> locateRoutesFromDB() {
        Map<String, ZuulProperties.ZuulRoute> routes = new LinkedHashMap<>();
        List<ZuulRouteVO> results = jdbcTemplate.query("select * from gateway_api_define where enabled = true ", new
                BeanPropertyRowMapper<>(ZuulRouteVO.class));
        for (ZuulRouteVO result : results) {
            if (StringUtils.isEmpty(result.getPath()) ) {
                continue;
            }
            if (StringUtils.isEmpty(result.getServiceId()) && StringUtils.isEmpty(result.getUrl())) {
                continue;
            }
            ZuulProperties.ZuulRoute zuulRoute = new ZuulProperties.ZuulRoute();
            try {
                BeanUtils.copyProperties(result, zuulRoute);
            } catch (Exception e) {
                logger.error("=============load zuul route info from db with error==============", e);
            }
            routes.put(zuulRoute.getPath(), zuulRoute);
        }
        return routes;
    }   
}
```
主要的是locateRoutes和locateRoutesFromDB这两个函数，locateRoutes是从SimpleRouteLocator Override过来的，先装载配置文件里面的路由信息，在从数据库里面获取路由信息，最后都是保存在SimpleRoteLocator 的AtomicReference<Map<String, ZuulRoute>> routes属性中，注意routes是类型，它是可以保证线程俺去的。

## 增加CustomZuulConfig类，主要是为了配置CustomRouteLocator
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
        CustomRouteLocator routeLocator = new CustomRouteLocator(this.server.getServlet().getPath(), this.zuulProperties);
        routeLocator.setJdbcTemplate(jdbcTemplate);
        return routeLocator;
    }
}
```
ustomerRouteLocator 去数据库获取路由配置信息，需要一个JdbcTemplate Bean。this.zuulProperties 就是配置文件里面的路由配置，应该是网关服务启动时自动就获取过来的。

## RefreshRouteService类，用于实现数据库路由信息的刷新
```
@Service
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

## 当然也要提供RefreshController，提供从浏览器访问的刷新功能
```@RestController
   public class RefreshController {
       @Autowired
       RefreshRouteService refreshRouteService;
   
       @Autowired
       ZuulHandlerMapping zuulHandlerMapping;
   
       @GetMapping("/refreshRoute")
       public String refresh() {
           refreshRouteService.refreshRoute();
           return "refresh success";
       }
   
       @RequestMapping("/watchRoute")
       public Object watchNowRoute() {
           //可以用debug模式看里面具体是什么
           return zuulHandlerMapping.getHandlerMap();
       }
   }
```
上面两个实现的功能是，在数据库里面新增或者修改路由信息，通过上面的功能进行刷新。


## 问题
网关服务跑起来了，也能实现正常的路由功能。但是，等等，查看日志，发现每隔30秒，服务自动从数据库再次加载路由配置，这是为什么呢？







