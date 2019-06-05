Zuul中包含了Hystrix和Ribbon的依赖，所以Zuul拥有线程隔离和断路器的自我保护功能，以及对服务调用的客户端负载均衡，需要注意的是传统路由也就是使用path与url映射关系来配置路由规则的时候,对于路由转发的请求不会使用HystrixCommand来包装，所以没有线程隔离和断路器的保护，并且也不会有负载均衡的能力。所以我们在使用Zuul的时候推荐使用path与serviceId的组合来进行配置。

**1. 设置Hystrix超时时间**
使用<code>hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds</code>来设置API网关中路由转发请求的命令执行时间超过配置值后，Hystrix会将该执行命令标记为TIMEOUT并抛出异常，Zuul会对该异常进行处理并返回如下JSON信息给外部调用方
```
    {
        "timestamp":20180705141032,
        "status":500,
        "error":"Internal Server Error",
        "exception":"com.netflix.zuul.exception.ZuulException",
        "message":"TIMEOUT"
    }
```

**2. 设置Ribbon连接超时时间**
使用<code>ribbon.ConnectTimeout</code>参数创建请求连接的超时时间，当ribbon.ConnectTimeout的配置值小于hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds的配置值时，若出现请求超时的时候，会自动进行重试路由请求，如果依然失败，Zuul会返回如下JSON信息给外部调用方

```aidl
{
    "timestamp":20180705141032,
    "status":500,
    "error":"Internal Server Error",
    "exception":"com.netflix.zuul.exception.ZuulException",
    "message":"NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED"
}
```
如果ribbon.ConnectTimeout的配置值大于hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds的配置值时，当出现请求超时的时候不会进行重试，直接超时处理返回TIMEOUT的错误信息

**3. 设置Ribbon的请求转发超时时间**
使用`ribbon.ReadTimeout`来设置请求转发超时时间，处理与ribbon.ConnectTimeout类似，不同点在于这是连接建立之后的处理时间。该值小于hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds的配置值时报TIMEOUT错误，反之报TIMEOUT的错误。小于的时候会先重试，不成才报错；大于的时候直接报错。

**4. 关闭重试配置**
全局配置`zuul.retryable=false`
针对路由配置`zuul.routes.<路由名>.retryable=false`

