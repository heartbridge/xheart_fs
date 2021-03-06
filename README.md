#xheart 文件服务器(基于netty框架 + RSA非对称加密)

### 安装

环境依赖：maven/gradle + jdk1.8

* `mvn clean package` 或 `gradle`

### 快速启动

* `java -jar xheart_fs-1.0.0-jar-with-dependencies.jar`

支持如下参数：

`--port`:文件服务器监听的端口，默认8585

`--baseDir`:文件存储的根目录，默认`/files/`

`--threshold`:文件压缩阀值(只对图片生效)，单位为：byte(字节)，默认为：1048576 bytes也即1M

`--allow`:允许访问的IP正则表达式，默认无白名单

`--deny`:不允许访问的IP正则表达式，默认无黑名单

`--daemon`:是否以守护线程运行.如果需要以守护线程运行，请使用`--daemon=on`，默认为主线程

`--basePackage`:需要扫描的基础包，用于扫描注册处理器`@Handler`

亦可以使用`startup.bat --help`或`./startup.sh --help`查看支持的参数

### 功能说明

#### 1、上传文件
```
[Post] http://ip:port
```

* 参数

`width`:【可选】缩略图的最大宽度，默认100px

`height`:【可选】缩略图的最大高度，默认100px

`isAvatar`:【可选】是否是头像处理，默认false.如果为头像图片处理，则会生成像素为100*100,200*200的缩略图，并且200*200为默认。如果不是头像图片处理，则会根据参数`width`和`height`来生成缩略图，并将缩略图设置为默认。

`files`:【必选】文件对象，可以多个

* 返回

```
{
  success:[//上传成功的文件列表
    {
      originName:xx,//文件原始名字
      fileName:xx,//文件保存的名字
      filePath:xx//文件保存的路径
    },
    ...
  ],
  failure:[//上传失败的文件列表
    {
      originName:xx,//文件原始名字
      errorMsg:xx//错误信息
    },
    ...
  ]
}
```

注意:返回的时候只会返回默认文件的路径，如果需要获取原始文件路径，则需要在文件名前加上`origin_`前缀，而对于头像，还有一个100*100的缩略图，可以使用`100_`前缀进行访问。比如：

在返回路径`filePath`为`/2015/7/28/12/25/57/1438057557408.jpg`，那么：
    
   原始图片路径为：`/2015/7/28/12/25/57/origin_1438057557408.jpg`
  
   如果为头像，100*100像素的图片地址为：`/2015/7/28/12/25/57/100_1438057557408.jpg`

### 2、获取文件

```
[Get] http://ip:port/{filePath}
```

* 参数

`filePath`:文件的路径，也即上传步骤中返回的`filePath`

* 返回

返回具体的文件对象，返回的`Content-Type`是根据文件后缀名对应的`Mime Type`决定的。比如：

`filePath`为`/2015/7/28/12/25/57/1438057557408.jpg`，那么响应的`Content-Type`为`image/jpeg`，具体的`Mime Type`对照请参照：[Mime Type对照表](http://www.w3school.com.cn/media/media_mimeref.asp)

### 3、服务器管理

xheart_fs提供了服务器管理功能。

#### 服务器的关闭

```
[Get] http://ip:port/management
```

* 参数
`signal`:`shutdown` 信号

`token`:授权串，生成方式为RSA非对称加密算法，使用[public.keystore公钥串](https://git.oschina.net/gavincook/xheart_fs/blob/master/src/main/resources/public.keystore)，对现在的时间（格式为yyyy-MM-dd HH）进行加密，加密后对密文进行Base64加密，由于需要在url上传递，因此还需要将最后的密文进行url编码。伪代码如下：
`encodeUrl(encodeBase64(encodeByPublicKey('2015-07-28 18')),'utf-8')`

* 返回

`shutdown successfully`

如果没有权限返回：

`Authorize failed`

#### 服务器状态查看


```
[Get] http://ip:port/management
```

* 参数
`signal`:`status` 信号

`token`:授权串，生成方式为RSA非对称加密算法，使用[public.keystore公钥串](https://git.oschina.net/gavincook/xheart_fs/blob/master/src/main/resources/public.keystore)，对现在的时间（格式为yyyy-MM-dd HH）进行加密，加密后对密文进行Base64加密，由于需要在url上传递，因此还需要将最后的密文进行url编码。伪代码如下：
`encodeUrl(encodeBase64(encodeByPublicKey('2015-07-28 18')),'utf-8')`

* 返回

```
启动参数：--port 8585 --name 2532 --baseDir D://netty 
启动时间：2015-07-28T18:57:17.329
监听端口：8585
文件基础路径：D://netty
图片压缩阀值：1 MB
```

如果没有权限返回：

`Authorize failed`


### IP过滤规则

1、首先检查是否有黑名单（deny）设置，如果有黑名单设置，将访问IP和黑名单规则进行匹配，如果匹配成功，则直接返回`403`状态，如果没有匹配成功或者没有黑名单设置，则跳转到第二步。

2、如果设置了白名单（allow），则进行白名单规则匹配，如果匹配成功则继续交给下一个处理器处理，否则返回`403`状态。如果没有设置白名单，把请求直接交给下一个处理器。

### 进阶
xheart 文件服务器支持Spring风格的插件扩展，可以自定义处理器，从而自定义处理流程。
在使用`mvn clean package`进行构建的时候，在构建目录下会生成一个xheart_fs{version}的文件夹。（version代表版本号，目前版本为1.0.0）
该目录下有两个文件夹，`bin`，`lib`。

* bin: 包含了`startup.bat`、`startup.sh`和`xheart_fs.jar`,用于启动整个文件服务器，应当始终使用`startup.bat`或`startup.sh`进行服务器启动。

* lib: 其它依赖的jar包都存放在该目录，比如netty依赖包

### 插件开发

支持Spring注解风格的处理器（插件）。将处理器的类使用`@Handler`标识，用`@RequestMapping`标识具体的处理方法。具体的处理方法支持使用`@RequestParam`注解接收对应的参数。

其中`@RequestParam`注解可以注入的值包括：

* 请求中的参数（支持字符参数和文件参数，形式支持单个参数、参数数组、参数集合，暂不支持实体对象参数）
* 通道上下文：`io.netty.channel.ChannelHandlerContext` 
* Http请求：`io.netty.handler.codec.http.HttpRequest`

#### 自定义处理器示例
1.首先添加依赖
```
<dependency>
    <groupId>com.github.heartbridge</groupId>
    <artifactId>xheart_fs</artifactId>
    <version>${xheart_fs.version}</version>
</dependency>
```
_目前最新版本为：1.0.1_

并添加远程镜像地址：
```
<repository>
    <id>nexus</id>
    <name>nexus releases</name>
    <url>http://nexus.xheart.cn:8081/content/repositories/releases/</url>
</repository>
```
2.自定义处理器
```
package com.heartbridge.helloworld;

import Handler;
import RequestMapping;

@Handler
public class SampleHandler {

    @RequestMapping(value = "/hello")
    public String hello(){
        return "Hello, xheart_fs";
    }
}
```

这里定义了一个示例处理器（@Handler标识），并且将`hello`方法映射为`/hello`的请求地址的处理方法。也即：当访问`/hello`时，返回字符串：`Hello, xheart_fs`

3.启动
服务器主类为：`Application`, 并且在启动时需要指定需要扫描的处理器的基础包名(`--basePackage`指定)。在本示例中, 示例处理器在`com.heartbridge`基础包下，启动命令如下：
`java  Application --basePackage com.heartbridge`

更多启动参数参见：[启动参数](https://github.com/heartbridge/xheart_fs#快速启动)

4.测试
打开浏览器，访问`http://loalhost:8585/hello`
