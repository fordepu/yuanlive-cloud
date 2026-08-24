package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.common.exception.ApiException;
import blog.yuanyuan.yuanlive.live.mapper.LiveRoomMapper;
import blog.yuanyuan.yuanlive.live.service.LiveCategoryRelationService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveCategoryServiceImplTest {

    @Test
    void deleteCategoriesRejectsCategoriesUsedByLiveRooms() {
        LiveCategoryRelationService relationService = mock(LiveCategoryRelationService.class);
        LiveRoomMapper liveRoomMapper = mock(LiveRoomMapper.class);
        when(relationService.count(any(Wrapper.class))).thenReturn(0L);
        when(liveRoomMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        LiveCategoryServiceImpl service = new LiveCategoryServiceImpl();
        ReflectionTestUtils.setField(service, "liveCategoryRelationService", relationService);
        ReflectionTestUtils.setField(service, "liveRoomMapper", liveRoomMapper);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.deleteCategories(List.of(10, 11)));

        assertEquals("该分类仍被2个直播间使用，不能删除", exception.getMessage());
        verify(liveRoomMapper).selectCount(any(Wrapper.class));
        verify(relationService, never()).remove(any(Wrapper.class));
    }
}
