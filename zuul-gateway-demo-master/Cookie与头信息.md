默认情况下，Zuul在请求路由时会过滤掉HTTP请求头信息中的一些敏感信息，防止这些敏感的头信息传递到下游外部服务器。但是如果我们使用安全框架如Spring Security、Apache Shiro等，需要使用Cookie做登录和鉴权，这时可以通过zuul.sensitiveHeaders参数定义，包括Cookie、Set-Cookie、Authorization三个属性来使Cookie可以被传递。
可以分为全局指定放行Cookie和Headers信息和指定路由放行
1.全局放行
```$xslt
zuul: 
  sensitiveHeaders: Cookie,Set-Cookie,Authorization
```

2.指定路由名放行
```$xslt
zuul:
  routes:
    users:
      path: /myusers/**
      sensitiveHeaders: Cookie,Set-Cookie,Authorization
      url: https://downstream
```

如果全局配置和路由配置均有不同程度的放行，那么采取就近原则，路由配置的放行规则将生效


