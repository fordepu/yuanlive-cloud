package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.common.exception.ApiException;
import blog.yuanyuan.yuanlive.common.result.ResultPage;
import blog.yuanyuan.yuanlive.entity.live.entity.LiveCategory;
import blog.yuanyuan.yuanlive.entity.live.entity.LiveCategoryRelation;
import blog.yuanyuan.yuanlive.entity.live.entity.LiveRoom;
import blog.yuanyuan.yuanlive.live.domain.dto.LiveCategoryDTO;
import blog.yuanyuan.yuanlive.live.domain.dto.LiveCategoryQueryDTO;
import blog.yuanyuan.yuanlive.live.domain.vo.HotCategoryVO;
import blog.yuanyuan.yuanlive.live.domain.vo.LiveCategoryTreeVO;
import blog.yuanyuan.yuanlive.live.domain.vo.LiveCategoryVO;
import blog.yuanyuan.yuanlive.entity.live.vo.LiveRoomRankVO;
import blog.yuanyuan.yuanlive.live.domain.vo.LiveChildVO;
import blog.yuanyuan.yuanlive.live.mapper.LiveCategoryMapper;
import blog.yuanyuan.yuanlive.live.mapper.LiveRoomMapper;
import blog.yuanyuan.yuanlive.live.properties.LiveRoomProperties;
import blog.yuanyuan.yuanlive.live.service.LiveCategoryRelationService;
import blog.yuanyuan.yuanlive.live.service.LiveCategoryService;
import blog.yuanyuan.yuanlive.live.util.PopularityUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
* @author frodepu
* @description 针对表【live_category(直播分类表)】的数据库操作Service实现
* @createDate 2026-01-27 14:55:18
*/
@Slf4j
@Service
public class LiveCategoryServiceImpl extends ServiceImpl<LiveCategoryMapper, LiveCategory>
    implements LiveCategoryService{
    @Resource
    private LiveRoomProperties liveRoomProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private PopularityUtil popularityUtil;
    @Resource
    private LiveCategoryMapper liveCategoryMapper;
    @Resource
    private LiveRoomMapper liveRoomMapper;
    @Resource
    private LiveCategoryRelationService liveCategoryRelationService;
    @Value("${live.hot.hot-categories}")
    private Integer hotCategories;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveCategory(LiveCategoryDTO categoryDTO) {
        // 检查同级别下是否已存在同名分类
        // 先获取所有具有相同父分类的分类ID列表
        List<Integer> siblingCategoryIds = new ArrayList<>();
        if (categoryDTO.getParentIds() != null && !categoryDTO.getParentIds().isEmpty()) {
            // 获取指定父分类下的所有子分类ID
            LambdaQueryWrapper<LiveCategoryRelation> relationQuery = new LambdaQueryWrapper<>();
            relationQuery.in(LiveCategoryRelation::getParentId, categoryDTO.getParentIds());
            List<LiveCategoryRelation> relations = liveCategoryRelationService.list(relationQuery);
            siblingCategoryIds = relations.stream().map(LiveCategoryRelation::getCategoryId).collect(Collectors.toList());
        } else {
            // 获取所有一级分类ID（即没有父分类的分类）
            // 先获取所有在关联表中存在的分类ID
            List<Integer> allRelatedCategoryIds = liveCategoryRelationService.list()
                .stream()
                .map(LiveCategoryRelation::getCategoryId)
                .collect(Collectors.toList());
            
            // 然后获取不在关联表中的分类（即一级分类）
            LambdaQueryWrapper<LiveCategory> categoryQuery = new LambdaQueryWrapper<>();
            if (!allRelatedCategoryIds.isEmpty()) {
                categoryQuery.notIn(LiveCategory::getId, allRelatedCategoryIds);
            }
            List<LiveCategory> topLevelCategories = this.list(categoryQuery);
            siblingCategoryIds = topLevelCategories.stream().map(LiveCategory::getId).collect(Collectors.toList());
        }
        
        // 检查同级分类中是否已存在同名分类
        if (!siblingCategoryIds.isEmpty()) {
            LambdaQueryWrapper<LiveCategory> nameCheckQuery = new LambdaQueryWrapper<>();
            nameCheckQuery.eq(LiveCategory::getName, categoryDTO.getName())
                          .in(LiveCategory::getId, siblingCategoryIds);
            if (this.count(nameCheckQuery) > 0) {
                log.warn("分类名称已存在: {}", categoryDTO.getName());
                throw new ApiException("同级别下已存在该分类名称");
            }
        } else {
            // 如果没有兄弟分类，只需检查该名称是否已存在于整个系统中（仅对于一级分类）
            if (categoryDTO.getParentIds() == null || categoryDTO.getParentIds().isEmpty()) {
                LambdaQueryWrapper<LiveCategory> nameCheckQuery = new LambdaQueryWrapper<>();
                nameCheckQuery.eq(LiveCategory::getName, categoryDTO.getName());
                if (this.count(nameCheckQuery) > 0) {
                    log.warn("分类名称已存在: {}", categoryDTO.getName());
                    throw new ApiException("同级别下已存在该分类名称");
                }
            }
        }

        LiveCategory category = new LiveCategory();
        BeanUtil.copyProperties(categoryDTO, category);
        
        // 如果没有设置排序权重，默认为0
        if (category.getSortWeight() == null) {
            category.setSortWeight(0);
        }
        
        boolean result = this.save(category);
        if (result && category.getId() != null) {
            // 处理关联关系
            if (categoryDTO.getParentIds() != null && !categoryDTO.getParentIds().isEmpty()) {
                // 检查父分类是否存在
                for (Integer parentId : categoryDTO.getParentIds()) {
                    LiveCategory parent = this.getById(parentId);
                    if (parent == null) {
                        log.warn("父分类不存在, parentId: {}", parentId);
                        throw new ApiException("父分类不存在");
                    }
                    
                    // 创建关联关系记录
                    LiveCategoryRelation relation = new LiveCategoryRelation();
                    relation.setParentId(parentId);
                    relation.setCategoryId(category.getId());
                    liveCategoryRelationService.save(relation);
                }
            }
        }
        
        log.info("分类新增{}, 分类ID: {}", result ? "成功" : "失败", category.getId());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCategory(LiveCategoryDTO categoryDTO) {
        log.info("开始修改分类, 分类ID: {}", categoryDTO.getId());
        
        if (categoryDTO.getId() == null) {
            log.warn("修改分类时分类ID不能为空");
            throw new ApiException("分类ID不能为空");
        }
        
        // 检查分类是否存在
        LiveCategory existCategory = this.getById(categoryDTO.getId());
        if (existCategory == null) {
            log.warn("分类不存在, 分类ID: {}", categoryDTO.getId());
            throw new ApiException("分类不存在");
        }

        // 检查同级别下是否已存在同名分类（排除自身）
        if (StrUtil.isNotBlank(categoryDTO.getName())) {
            // 获取当前分类的兄弟分类ID（包括当前分类原来的兄弟和新位置的兄弟）
            Set<Integer> siblingCategoryIds = new HashSet<>();
            
            // 获取新父分类下的兄弟分类（如果更改了父分类）
            List<Integer> newParentIds = categoryDTO.getParentIds();
            if (newParentIds != null && !newParentIds.isEmpty()) {
                // 检查新父分类是否存在
                for (Integer newParentId : newParentIds) {
                    LiveCategory newParent = this.getById(newParentId);
                    if (newParent == null) {
                        log.warn("新父分类不存在, parentId: {}", newParentId);
                        throw new ApiException("新父分类不存在");
                    }
                }
                
                // 获取新父分类下的所有子分类ID
                LambdaQueryWrapper<LiveCategoryRelation> newRelationQuery = new LambdaQueryWrapper<>();
                newRelationQuery.in(LiveCategoryRelation::getParentId, newParentIds);
                List<LiveCategoryRelation> newRelations = liveCategoryRelationService.list(newRelationQuery);
                siblingCategoryIds.addAll(newRelations.stream()
                    .filter(rel -> !rel.getCategoryId().equals(categoryDTO.getId())) // 排除自己
                    .map(LiveCategoryRelation::getCategoryId)
                    .collect(Collectors.toList()));
            } else {
                // 如果是顶级分类（没有父分类）
                if (newParentIds == null || newParentIds.isEmpty()) {
                    List<Integer> allRelatedCategoryIds = liveCategoryRelationService.list()
                        .stream()
                        .map(LiveCategoryRelation::getCategoryId)
                        .collect(Collectors.toList());
                    
                    LambdaQueryWrapper<LiveCategory> categoryQuery = new LambdaQueryWrapper<>();
                    categoryQuery.ne(LiveCategory::getId, categoryDTO.getId()); // 排除自己
                    if (!allRelatedCategoryIds.isEmpty()) {
                        categoryQuery.notIn(LiveCategory::getId, allRelatedCategoryIds);
                    }
                    List<LiveCategory> topLevelSiblings = this.list(categoryQuery);
                    siblingCategoryIds.addAll(topLevelSiblings.stream().map(LiveCategory::getId).collect(Collectors.toList()));
                }
            }
            
            // 检查是否有同名分类
            if (!siblingCategoryIds.isEmpty()) {
                LambdaQueryWrapper<LiveCategory> nameCheckQuery = new LambdaQueryWrapper<>();
                nameCheckQuery.eq(LiveCategory::getName, categoryDTO.getName())
                              .in(LiveCategory::getId, siblingCategoryIds);
                if (this.count(nameCheckQuery) > 0) {
                    log.warn("分类名称已存在: {}", categoryDTO.getName());
                    throw new ApiException("同级别下已存在该分类名称");
                }
            }
        }
        
        // 更新分类基本信息
        LiveCategory category = new LiveCategory();
        BeanUtil.copyProperties(categoryDTO, category);
        boolean result = this.updateById(category);
        
        if (result) {
            // 处理父分类关系变更
            if (categoryDTO.getParentIds() != null) {
                // 检查父分类不能为自身
                if (categoryDTO.getParentIds().contains(categoryDTO.getId())) {
                    log.warn("父分类不能为自身, 分类ID: {}", categoryDTO.getId());
                    throw new ApiException("父分类不能为自身");
                }
                
                // 删除旧的关联关系
                LambdaQueryWrapper<LiveCategoryRelation> oldRelationQuery = new LambdaQueryWrapper<>();
                oldRelationQuery.eq(LiveCategoryRelation::getCategoryId, categoryDTO.getId());
                liveCategoryRelationService.remove(oldRelationQuery);
                
                // 如果新的父分类ID不为空，建立新的关联关系
                if (categoryDTO.getParentIds() != null && !categoryDTO.getParentIds().isEmpty()) {
                    for (Integer parentId : categoryDTO.getParentIds()) {
                        LiveCategory parent = this.getById(parentId);
                        if (parent == null) {
                            log.warn("父分类不存在, parentId: {}", parentId);
                            throw new ApiException("父分类不存在");
                        }
                        
                        // 创建新的关联关系
                        LiveCategoryRelation newRelation = new LiveCategoryRelation();
                        newRelation.setParentId(parentId);
                        newRelation.setCategoryId(category.getId());
                        liveCategoryRelationService.save(newRelation);
                    }
                }
            }
        }
        
        log.info("分类修改{}, 分类ID: {}", result ? "成功" : "失败", category.getId());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCategories(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            log.warn("删除分类IDs为空");
            throw new ApiException("请选择要删除的分类");
        }
        
        // 检查要删除的分类是否作为父分类被其他分类引用
        LambdaQueryWrapper<LiveCategoryRelation> parentCheckQuery = new LambdaQueryWrapper<>();
        parentCheckQuery.in(LiveCategoryRelation::getParentId, ids);
        long childCount = liveCategoryRelationService.count(parentCheckQuery);
        if (childCount > 0) {
            log.warn("存在子分类，不能删除");
            throw new ApiException("该分类下存在子分类，请先删除子分类");
        }
        
        LambdaQueryWrapper<LiveRoom> roomCheckQuery = new LambdaQueryWrapper<>();
        roomCheckQuery.in(LiveRoom::getCategoryId, ids);
        Long roomCount = liveRoomMapper.selectCount(roomCheckQuery);
        if (roomCount != null && roomCount > 0) {
            log.warn("分类仍被直播间使用，不能删除: categoryIds={}, roomCount={}", ids, roomCount);
            throw new ApiException("该分类仍被" + roomCount + "个直播间使用，不能删除");
        }
        
        // 删除分类及其关联关系
        boolean result = this.removeByIds(ids);
        
        if (result) {
            // 删除关联表中的记录
            LambdaQueryWrapper<LiveCategoryRelation> relationQuery = new LambdaQueryWrapper<>();
            relationQuery.or().in(LiveCategoryRelation::getParentId, ids)
                         .or().in(LiveCategoryRelation::getCategoryId, ids);
            liveCategoryRelationService.remove(relationQuery);
        }
        
        log.info("分类删除{}, 删除数量: {}", result ? "成功" : "失败", ids.size());
        return result;
    }

    @Override
    public LiveCategoryVO getCategoryById(Integer id) {
        if (id == null) {
            log.warn("分类ID不能为空");
            throw new ApiException("分类ID不能为空");
        }
        
        LiveCategory category = this.getById(id);
        if (category == null) {
            log.warn("分类不存在, 分类ID: {}", id);
            throw new ApiException("分类不存在");
        }
        List<LiveCategoryRelation> relationList = liveCategoryRelationService.lambdaQuery()
                .eq(LiveCategoryRelation::getCategoryId, id).list();
        List<Integer> parentIds = relationList.stream().map(LiveCategoryRelation::getParentId).toList();

        LiveCategoryVO categoryVO = BeanUtil.copyProperties(category, LiveCategoryVO.class);
        categoryVO.setParentIds(parentIds);
        return categoryVO;
    }

    @Override
    public ResultPage<LiveCategoryVO> pageCategories(LiveCategoryQueryDTO queryDTO) {
        // 构建查询条件
        Page<LiveCategory> page;
        if (queryDTO.getParentIds() != null) {
            // 如果指定了父分类IDs，需要通过关联表查询
            if (!queryDTO.getParentIds().isEmpty()) {
                // 获取指定父分类的所有子分类ID
                LambdaQueryWrapper<LiveCategoryRelation> relationQuery = new LambdaQueryWrapper<>();
                relationQuery.in(LiveCategoryRelation::getParentId, queryDTO.getParentIds());
                List<LiveCategoryRelation> relations = liveCategoryRelationService.list(relationQuery);
                List<Integer> childIds = relations.stream()
                    .map(LiveCategoryRelation::getCategoryId)
                    .collect(Collectors.toList());
                
                // 查询这些子分类
                if (childIds.isEmpty()) {
                    // 如果没有子分类，返回空结果
                    page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize(), 0);
                    page.setRecords(Collections.emptyList());
                } else {
                    Page<LiveCategory> tempPage = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
                    LambdaQueryWrapper<LiveCategory> categoryQuery = new LambdaQueryWrapper<>();
                    categoryQuery.in(LiveCategory::getId, childIds)
                                .like(StrUtil.isNotBlank(queryDTO.getName()), LiveCategory::getName, queryDTO.getName())
                                .orderByDesc(LiveCategory::getSortWeight)
                                .orderByAsc(LiveCategory::getId);
                    page = this.page(tempPage, categoryQuery);
                }
            } else {
                // 查询顶级分类（不在关联表中作为子分类出现的分类）
                List<Integer> allRelatedCategoryIds = liveCategoryRelationService.list()
                    .stream()
                    .map(LiveCategoryRelation::getCategoryId)
                    .collect(Collectors.toList());
                
                Page<LiveCategory> tempPage = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
                LambdaQueryWrapper<LiveCategory> categoryQuery = new LambdaQueryWrapper<>();
                categoryQuery.like(StrUtil.isNotBlank(queryDTO.getName()), LiveCategory::getName, queryDTO.getName());
                if (!allRelatedCategoryIds.isEmpty()) {
                    categoryQuery.notIn(LiveCategory::getId, allRelatedCategoryIds);
                }
                categoryQuery.orderByDesc(LiveCategory::getSortWeight)
                             .orderByAsc(LiveCategory::getId);
                page = this.page(tempPage, categoryQuery);
            }
        } else {
            // 不按父分类过滤，只按名称搜索
            Page<LiveCategory> tempPage = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
            LambdaQueryWrapper<LiveCategory> categoryQuery = new LambdaQueryWrapper<>();
            categoryQuery.like(StrUtil.isNotBlank(queryDTO.getName()), LiveCategory::getName, queryDTO.getName())
                        .orderByDesc(LiveCategory::getSortWeight)
                        .orderByAsc(LiveCategory::getId);
            page = this.page(tempPage, categoryQuery);
        }

        List<LiveCategoryVO> voList = page.getRecords().stream()
                .map(category -> {
                    LiveCategoryVO vo = BeanUtil.copyProperties(category, LiveCategoryVO.class);
                    // 获取该分类的所有父级分类ID
                    List<LiveCategoryRelation> relations = liveCategoryRelationService.lambdaQuery()
                        .eq(LiveCategoryRelation::getCategoryId, category.getId())
                        .list();
                    List<Integer> parentIds = relations.stream()
                        .map(LiveCategoryRelation::getParentId)
                        .collect(Collectors.toList());
                    
                    // 如果没有父级分类，说明是一级分类
                    if (parentIds.isEmpty()) {
                        parentIds = List.of(0);
                    }
                    
                    vo.setParentIds(parentIds);
                    return vo;
                })
                .collect(Collectors.toList());
        Page<LiveCategoryVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return ResultPage.of(voPage);
    }

    @Override
    public List<LiveCategoryTreeVO> treeList() {
        // 查询所有分类
        List<LiveCategory> allCategories = this.list(
                new LambdaQueryWrapper<LiveCategory>()
                        .orderByDesc(LiveCategory::getSortWeight)
                        .orderByAsc(LiveCategory::getId)
        );
        
        // 查询所有关联关系
        List<LiveCategoryRelation> allRelations = liveCategoryRelationService.list();
        
        // 转换为TreeVO（只包含parentId，不包含parentIds）
        List<LiveCategoryTreeVO> allTreeVOs = allCategories.stream()
                .map(category -> {
                    LiveCategoryTreeVO vo = BeanUtil.copyProperties(category, LiveCategoryTreeVO.class);
                    // 只设置parentId（从关联表中获取第一个父分类ID）
                    List<Integer> parentIds = allRelations.stream()
                        .filter(relation -> relation.getCategoryId().equals(category.getId()))
                        .map(LiveCategoryRelation::getParentId)
                        .collect(Collectors.toList());
                    // 为了向后兼容，设置第一个parentId
                    if (!parentIds.isEmpty()) {
                        vo.setParentId(parentIds.get(0));
                    } else {
                        vo.setParentId(0); // 顶级分类
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        
        // 使用关联关系构建树形结构
        return buildTreeFromRelationsForTree(allTreeVOs, allRelations, 0);
    }

    @Override
    public List<LiveCategoryVO> getFirstLevelCategories() {
        log.info("获取一级分类列表");

        // 1. 查询所有“子分类”的ID（即在关联表中 parentId 不为 0 的记录）
        List<Integer> subCategoryIds = liveCategoryRelationService.lambdaQuery()
                .ne(LiveCategoryRelation::getParentId, 0)
                .list()
                .stream()
                .map(LiveCategoryRelation::getCategoryId)
                .collect(Collectors.toList());

        // 2. 构造查询条件：只要分类 ID 不在“子分类 ID 列表”中，它就是一级分类
        LambdaQueryWrapper<LiveCategory> categoryQuery = new LambdaQueryWrapper<>();
        if (!subCategoryIds.isEmpty()) {
            categoryQuery.notIn(LiveCategory::getId, subCategoryIds);
        }

        // 排序逻辑保持不变
        categoryQuery.orderByDesc(LiveCategory::getSortWeight)
                .orderByAsc(LiveCategory::getId);

        List<LiveCategory> topLevelCategories = this.list(categoryQuery);

        // 3. 封装为 VO 返回
        return topLevelCategories.stream()
                .map(category -> {
                    LiveCategoryVO vo = BeanUtil.copyProperties(category, LiveCategoryVO.class);
                    vo.setParentIds(List.of(0)); // 一级分类的逻辑父ID统一设为0
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<LiveRoomRankVO> getLiveRoomsByCategoryValue(String value) {
        List<Integer> categoryIds;
        
        // 如果 value 为空，则查询所有类别的直播间
        if (StrUtil.isBlank(value)) {
            // 获取所有分类ID
            List<LiveCategory> allCategories = this.list();
            categoryIds = allCategories.stream()
                    .map(LiveCategory::getId)
                    .collect(Collectors.toList());
        } else {
            // 根据 value 获取对应的分类
            LambdaQueryWrapper<LiveCategory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(LiveCategory::getValue, value);
            LiveCategory category = this.getOne(queryWrapper);
            
            if (category == null) {
                return List.of(); // 如果没找到对应的分类，返回空列表
            }
            
            // 检查是否是父类ID，如果是则获取所有子类别ID
            categoryIds = getCategoryIdsWithChildren(category.getId());
        }
        
        // 收集所有相关的直播间ID
        Set<String> allRoomIds = new HashSet<>();
        for (Integer categoryId : categoryIds) {
            String categoryKey = liveRoomProperties.getCategoryRoomsPrefix() + categoryId;
            Set<String> roomIds = stringRedisTemplate.opsForSet().members(categoryKey);
            if (!CollUtil.isEmpty(roomIds)) {
                allRoomIds.addAll(roomIds);
            }
        }
        
        if (CollUtil.isEmpty(allRoomIds)) {
            return List.of();
        }
        
        List<String> roomIdList = new ArrayList<>(allRoomIds);
        return popularityUtil.getPopularRoomVOS(roomIdList);
    }

    @Override
    public List<HotCategoryVO> getHotCategory() {
        String rankKey = liveRoomProperties.getCategoryRank();
        Set<String> categoryIds = stringRedisTemplate.opsForZSet()
                .reverseRange(rankKey, 0, hotCategories - 1);
        List<Integer> ids = categoryIds.stream().map(Integer::parseInt).toList();
        if (CollUtil.isEmpty(categoryIds)) return List.of();
        return liveCategoryMapper.getHotCategories(ids);
    }

    @Override
    public Integer getCategoryIdBySearch(String keyword) {
        List<LiveCategory> categories = this.lambdaQuery()
                .select(LiveCategory::getId)
                .like(LiveCategory::getName, keyword)
                .or()
                .like(LiveCategory::getValue, keyword)
                .list();
        // 返回最多的类型id
        return categories.stream()
                .collect(Collectors.groupingBy(LiveCategory::getId, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    @Override
    public List<LiveChildVO> getChildren() {
        // 获取所有有关联关系的分类（即有父分类的分类）
        List<Integer> ids = liveCategoryRelationService.lambdaQuery()
                .ne(LiveCategoryRelation::getParentId, 0)
                .list()
                .stream()
                .map(LiveCategoryRelation::getCategoryId)
                .distinct()
                .toList();
        
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 获取这些分类的详细信息
        List<LiveCategory> allCategories = this.listByIds(ids);
        
        // 转换为 VO
        List<LiveChildVO> categoryVOs = allCategories.stream()
                .map(category -> {
                    LiveChildVO vo = new LiveChildVO();
                    vo.setId(category.getId());
                    vo.setLabel(category.getName());
                    vo.setValue(category.getValue());
                    return vo;
                })
                .toList();
        
        return categoryVOs;
    }

    /**
     * 获取指定分类ID及其所有子分类ID
     * @param categoryId 分类ID
     * @return 包含分类ID及其所有子分类ID的列表
     */
    private List<Integer> getCategoryIdsWithChildren(Integer categoryId) {
        List<Integer> categoryIds = new ArrayList<>();
        categoryIds.add(categoryId);
        
        // 通过关联表获取所有直接子分类
        LambdaQueryWrapper<LiveCategoryRelation> relationQuery = new LambdaQueryWrapper<>();
        relationQuery.eq(LiveCategoryRelation::getParentId, categoryId);
        List<LiveCategoryRelation> relations = liveCategoryRelationService.list(relationQuery);
        
        // 递归获取更深层级的子分类
        for (LiveCategoryRelation relation : relations) {
            categoryIds.addAll(getCategoryIdsWithChildren(relation.getCategoryId()));
        }
        
        return categoryIds;
    }

    
    /**
     * 基于关联关系构建树形结构（用于LiveCategoryTreeVO）
     * @param allCategories 所有分类
     * @param allRelations 所有关联关系
     * @param parentId 父ID
     * @return 树形结构
     */
    private List<LiveCategoryTreeVO> buildTreeFromRelationsForTree(List<LiveCategoryTreeVO> allCategories, List<LiveCategoryRelation> allRelations, Integer parentId) {
        List<LiveCategoryTreeVO> tree = new ArrayList<>();

        for (LiveCategoryTreeVO category : allCategories) {
            boolean isChild = false;
            // 1. 检查是否是当前 parentId 的子节点
            for (LiveCategoryRelation relation : allRelations) {
                if (relation.getCategoryId().equals(category.getId()) && relation.getParentId().equals(parentId)) {
                    isChild = true;
                    break;
                }
            }

            // 2. 顶级节点逻辑（无父关系的即为顶级）
            if (parentId == 0) {
                boolean hasParent = false;
                for (LiveCategoryRelation relation : allRelations) {
                    if (relation.getCategoryId().equals(category.getId())) {
                        hasParent = true;
                        break;
                    }
                }
                if (!hasParent) isChild = true;
            }

            if (isChild) {
                // --- 核心修正：创建一个新的 VO 实例，防止引用污染 ---
                LiveCategoryTreeVO newNode = new LiveCategoryTreeVO();
                newNode.setId(category.getId());
                newNode.setName(category.getName());
                newNode.setIconUrl(category.getIconUrl());
                newNode.setValue(category.getValue());
                newNode.setParentId(parentId); // 设置该分支特有的 parentId

                // 递归查找子分类
                List<LiveCategoryTreeVO> children = buildTreeFromRelationsForTree(allCategories, allRelations, category.getId());
                if (!children.isEmpty()) {
                    newNode.setChildren(children);
                }

                tree.add(newNode);
            }
        }

        return tree;
    }
}



