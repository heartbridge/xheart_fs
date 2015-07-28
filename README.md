#xheart 文件服务器(基于netty框架 + RSA非对称加密)

### 安装

* `mvn clean package`

### 启动

* `java -jar xheart_fs-1.0.0-jar-with-dependencies.jar`

支持如下参数：

`--port`:文件服务器监听的端口，默认8585

`--baseDir`:文件存储的根目录，默认`/files/`

`--threshold`:文件压缩阀值(只对图片生效)，单位为：byte(字节)，默认为：1048576 bytes也即1M.

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

xheart_fs提供了服务器管理功能。目前暂时只支持服务器的关闭。

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