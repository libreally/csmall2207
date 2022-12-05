package cn.tedu.mall.search.repository;

import cn.tedu.mall.pojo.search.entity.SpuForElastic;
import com.github.pagehelper.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpuForElasticRepository  extends
        ElasticsearchRepository<SpuForElastic,Long> {
    //查询title字段包含指定关键字的spu数据
    Iterable<SpuForElastic> querySpuForElasticsByTitleMatches(String title);
    @Query("{\n" +
            "    \"bool\": {\n" +
            "      \"should\": [\n" +
            "        { \"match\": { \"name\": \"?0\"}},\n" +
            "        { \"match\": { \"title\": \"?0\"}},\n" +
            "        { \"match\": { \"description\": \"?0\"}},\n" +
            "        { \"match\": { \"category_name\": \"?0\"}}\n" +
            "        ]\n" +
            "     }\n" +
            "}")
        // 上面指定了查询语句的情况下,自定义方法的方法名就可以随意起名了
    Page<SpuForElastic> querySearch(String keyword, Pageable pageable);

}
