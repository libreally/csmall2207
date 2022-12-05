package cn.tedu.mall.search.controller;


import cn.tedu.mall.common.restful.JsonPage;
import cn.tedu.mall.common.restful.JsonResult;
import cn.tedu.mall.pojo.search.entity.SpuForElastic;
import cn.tedu.mall.search.service.ISearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@Api(tags = "搜索模块")
public class SearchController {
    @Autowired
    private ISearchService searchService;

    @GetMapping
    @ApiOperation("根据用户关键字分页搜索商品信息")
    @ApiImplicitParams({
                    @ApiImplicitParam(value = "搜索关键字" ,name = "keyword" ,example = "手机"),
                    @ApiImplicitParam(value = "页码" ,name = "page" ,example = "1"),
                    @ApiImplicitParam(value = "每页条数" ,name = "pageSize" ,example = "2")
            })
    public JsonResult<JsonPage<SpuForElastic>> searchByKeyword(String keyword,Integer page,Integer pageSize){
        JsonPage<SpuForElastic> search = searchService.search(keyword, page, pageSize);
        return JsonResult.ok(search);
    }
}
