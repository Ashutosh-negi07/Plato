package com.miniproject.plato.modules.restaurant;

import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.modules.restaurant.dto.CreateRestaurantRequest;
import com.miniproject.plato.modules.restaurant.dto.RestaurantResponse;
import com.miniproject.plato.modules.restaurant.dto.RestaurantSettingsRequest;
import com.miniproject.plato.modules.restaurant.dto.UpdateRestaurantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// =============================================================================
// RestaurantServiceImplTest
// -----------------------------------------------------------------------------
// Unit tests for RestaurantServiceImpl using JUnit 5 + Mockito.
//
// Key concepts used here:
//   @ExtendWith(MockitoExtension.class) → activates Mockito for this test class
//   @Mock                               → creates a fake/stub dependency
//   @InjectMocks                        → creates the real class and injects mocks
//   @Nested                             → groups related tests for readability
//   @DisplayName                        → human-readable test names
//   when(...).thenReturn(...)           → stubbing (telling mock what to return)
//   verify(...)                         → assert that a method was called
//   assertThatThrownBy(...)             → assert that an exception is thrown
// =============================================================================
@ExtendWith(MockitoExtension.class)
class RestaurantServiceImplTest {

    // ── Mocks (fakes — no real DB, no real Spring context) ────────────────────
    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantMapper restaurantMapper;

    // ── Subject Under Test ────────────────────────────────────────────────────
    // Mockito creates RestaurantServiceImpl and injects the two mocks above.
    @InjectMocks
    private RestaurantServiceImpl restaurantService;

    // ── Shared test data ──────────────────────────────────────────────────────
    private UUID ownerId;
    private UUID restaurantId;
    private UUID strangerId;
    private Restaurant restaurant;
    private RestaurantResponse restaurantResponse;

    @BeforeEach
    void setUp() {
        ownerId      = UUID.randomUUID();
        restaurantId = UUID.randomUUID();
        strangerId   = UUID.randomUUID();

        restaurant = Restaurant.builder()
                .ownerId(ownerId)
                .name("Test Restaurant")
                .status(RestaurantStatus.ACTIVE)
                .taxPercentage(BigDecimal.ZERO)
                .serviceCharge(BigDecimal.ZERO)
                .allowCashPayment(true)
                .allowCardPayment(true)
                .allowUpi(true)
                .allowOnlinePayment(false)
                .acceptingOrders(true)
                .autoAcceptOrders(false)
                .build();

        restaurantResponse = RestaurantResponse.builder()
                .id(restaurantId)
                .ownerId(ownerId)
                .name("Test Restaurant")
                .status(RestaurantStatus.ACTIVE)
                .build();
    }

    // =========================================================================
    // createRestaurant
    // =========================================================================
    @Nested
    @DisplayName("createRestaurant()")
    class CreateRestaurant {

        @Test
        @DisplayName("should create and return a RestaurantResponse")
        void shouldCreateRestaurant() {
            CreateRestaurantRequest request = new CreateRestaurantRequest(
                    "Test Restaurant",
                    null, null, null,
                    null, null, null, null, null,
                    null, null, null,
                    null, null,
                    null, null, null, null, null, null
            );

            when(restaurantMapper.toEntity(request, ownerId)).thenReturn(restaurant);
            when(restaurantRepository.save(restaurant)).thenReturn(restaurant);
            when(restaurantMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

            RestaurantResponse result = restaurantService.createRestaurant(request, ownerId);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Test Restaurant");
            assertThat(result.getOwnerId()).isEqualTo(ownerId);

            verify(restaurantMapper).toEntity(request, ownerId);
            verify(restaurantRepository).save(restaurant);
            verify(restaurantMapper).toResponse(restaurant);
        }
    }

    // =========================================================================
    // getAllRestaurants
    // =========================================================================
    @Nested
    @DisplayName("getAllRestaurants()")
    class GetAllRestaurants {

        private Pageable pageable;

        @BeforeEach
        void init() {
            pageable = PageRequest.of(0, 10);
        }

        @Test
        @DisplayName("SUPER_ADMIN should see all restaurants")
        void superAdminSeesAll() {
            Page<Restaurant> page = new PageImpl<>(List.of(restaurant));
            when(restaurantRepository.findAll(pageable)).thenReturn(page);
            when(restaurantMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

            Page<RestaurantResponse> result = restaurantService.getAllRestaurants(
                    UUID.randomUUID(), "SUPER_ADMIN", pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(restaurantRepository).findAll(pageable);
            verify(restaurantRepository, never()).findByOwnerId(any(), any());
        }

        @Test
        @DisplayName("OWNER should only see their own restaurants")
        void ownerSeesOnlyOwn() {
            Page<Restaurant> page = new PageImpl<>(List.of(restaurant));
            when(restaurantRepository.findByOwnerId(ownerId, pageable)).thenReturn(page);
            when(restaurantMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

            Page<RestaurantResponse> result = restaurantService.getAllRestaurants(
                    ownerId, "OWNER", pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(restaurantRepository).findByOwnerId(ownerId, pageable);
            verify(restaurantRepository, never()).findAll(any(Pageable.class));
        }
    }

    // =========================================================================
    // getRestaurantById
    // =========================================================================
    @Nested
    @DisplayName("getRestaurantById()")
    class GetRestaurantById {

        @Test
        @DisplayName("SUPER_ADMIN can view any restaurant")
        void superAdminCanViewAny() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
            when(restaurantMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

            RestaurantResponse result = restaurantService.getRestaurantById(
                    restaurantId, strangerId, "SUPER_ADMIN");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(restaurantId);
        }

        @Test
        @DisplayName("OWNER can view their own restaurant")
        void ownerCanViewOwn() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
            when(restaurantMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

            RestaurantResponse result = restaurantService.getRestaurantById(
                    restaurantId, ownerId, "OWNER");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("OWNER cannot view another owner's restaurant → UnauthorizedAccessException")
        void ownerCannotViewOthers() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

            assertThatThrownBy(() ->
                    restaurantService.getRestaurantById(restaurantId, strangerId, "OWNER")
            ).isInstanceOf(UnauthorizedAccessException.class);
        }

        @Test
        @DisplayName("Non-existent restaurant → ResourceNotFoundException")
        void notFound() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    restaurantService.getRestaurantById(restaurantId, ownerId, "OWNER")
            ).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // updateRestaurant
    // =========================================================================
    @Nested
    @DisplayName("updateRestaurant()")
    class UpdateRestaurant {

        @Test
        @DisplayName("Owner can update their own restaurant")
        void ownerCanUpdate() {
            UpdateRestaurantRequest request = new UpdateRestaurantRequest(
                    "Updated Name", null, null, null,
                    null, null, null, null, null, null, null, null
            );
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
            when(restaurantMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

            RestaurantResponse result = restaurantService.updateRestaurant(
                    restaurantId, request, ownerId);

            assertThat(result).isNotNull();
            verify(restaurantMapper).applyUpdate(restaurant, request);
            // Soft update — dirty checking, NO explicit save()
            verify(restaurantRepository, never()).save(any());
        }

        @Test
        @DisplayName("Non-owner cannot update → UnauthorizedAccessException")
        void strangerCannotUpdate() {
            UpdateRestaurantRequest request = new UpdateRestaurantRequest(
                    "Hacked Name", null, null, null,
                    null, null, null, null, null, null, null, null
            );
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

            assertThatThrownBy(() ->
                    restaurantService.updateRestaurant(restaurantId, request, strangerId)
            ).isInstanceOf(UnauthorizedAccessException.class);

            verify(restaurantMapper, never()).applyUpdate(any(), any());
        }

        @Test
        @DisplayName("Non-existent restaurant → ResourceNotFoundException")
        void notFound() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());
            UpdateRestaurantRequest request = new UpdateRestaurantRequest(
                    "Name", null, null, null,
                    null, null, null, null, null, null, null, null
            );

            assertThatThrownBy(() ->
                    restaurantService.updateRestaurant(restaurantId, request, ownerId)
            ).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // updateSettings
    // =========================================================================
    @Nested
    @DisplayName("updateSettings()")
    class UpdateSettings {

        @Test
        @DisplayName("Owner can update settings")
        void ownerCanUpdateSettings() {
            RestaurantSettingsRequest request = new RestaurantSettingsRequest(
                    new BigDecimal("5.00"), new BigDecimal("2.50"),
                    true, true, true, false, true, false
            );
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
            when(restaurantMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

            RestaurantResponse result = restaurantService.updateSettings(
                    restaurantId, request, ownerId);

            assertThat(result).isNotNull();
            verify(restaurantMapper).applySettings(restaurant, request);
        }

        @Test
        @DisplayName("Non-owner cannot update settings → UnauthorizedAccessException")
        void strangerCannotUpdateSettings() {
            RestaurantSettingsRequest request = new RestaurantSettingsRequest(
                    null, null, null, null, null, null, null, null
            );
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

            assertThatThrownBy(() ->
                    restaurantService.updateSettings(restaurantId, request, strangerId)
            ).isInstanceOf(UnauthorizedAccessException.class);

            verify(restaurantMapper, never()).applySettings(any(), any());
        }
    }

    // =========================================================================
    // updateStatus  (SUPER_ADMIN only — no ownership check)
    // =========================================================================
    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("Should update status to INACTIVE and return response")
        void shouldUpdateStatus() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
            when(restaurantMapper.toResponse(restaurant)).thenReturn(restaurantResponse);

            RestaurantResponse result = restaurantService.updateStatus(
                    restaurantId, RestaurantStatus.INACTIVE);

            assertThat(result).isNotNull();
            // Entity status must have been mutated
            assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.INACTIVE);
        }

        @Test
        @DisplayName("Non-existent restaurant → ResourceNotFoundException")
        void notFound() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    restaurantService.updateStatus(restaurantId, RestaurantStatus.INACTIVE)
            ).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // deleteRestaurant  (soft delete — sets status to INACTIVE)
    // =========================================================================
    @Nested
    @DisplayName("deleteRestaurant()")
    class DeleteRestaurant {

        @Test
        @DisplayName("Owner can soft-delete their restaurant")
        void ownerCanDelete() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

            restaurantService.deleteRestaurant(restaurantId, ownerId);

            // Soft delete → status becomes INACTIVE
            assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.INACTIVE);
            // Hard delete should NEVER be called
            verify(restaurantRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("Non-owner cannot delete → UnauthorizedAccessException")
        void strangerCannotDelete() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));

            assertThatThrownBy(() ->
                    restaurantService.deleteRestaurant(restaurantId, strangerId)
            ).isInstanceOf(UnauthorizedAccessException.class);

            // Status must remain ACTIVE — delete was aborted
            assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        }

        @Test
        @DisplayName("Non-existent restaurant → ResourceNotFoundException")
        void notFound() {
            when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    restaurantService.deleteRestaurant(restaurantId, ownerId)
            ).isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
