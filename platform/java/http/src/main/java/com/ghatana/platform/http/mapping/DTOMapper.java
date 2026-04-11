package com.ghatana.platform.http.mapping;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic DTO (Data Transfer Object) mapper for transforming between
 * domain models and API response/request DTOs.
 *
 * @doc.type interface
 * @doc.purpose Provides a standardized interface for DTO mapping
 * @doc.layer platform
 * @doc.pattern Mapper
 * @param <D> Domain model type
 * @param <T> DTO type
 */
public interface DTOMapper<D, T> {

    /**
     * Convert a domain model to a DTO.
     *
     * @param domain the domain model
     * @return the DTO representation
     */
    T toDto(D domain);

    /**
     * Convert a DTO to a domain model.
     *
     * @param dto the DTO
     * @return the domain model
     */
    D toDomain(T dto);

    /**
     * Convert a list of domain models to DTOs.
     *
     * @param domains list of domain models
     * @return list of DTOs
     */
    default List<T> toDtoList(List<D> domains) {
        if (domains == null) {
            return null;
        }
        return domains.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Convert a list of DTOs to domain models.
     *
     * @param dtos list of DTOs
     * @return list of domain models
     */
    default List<D> toDomainList(List<T> dtos) {
        if (dtos == null) {
            return null;
        }
        return dtos.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Create a mapper from two functions.
     *
     * @param toDtoFunction function to convert domain to DTO
     * @param toDomainFunction function to convert DTO to domain
     * @param <D> domain type
     * @param <T> DTO type
     * @return a DTO mapper
     */
    static <D, T> DTOMapper<D, T> of(Function<D, T> toDtoFunction, Function<T, D> toDomainFunction) {
        return new DTOMapper<>() {
            @Override
            public T toDto(D domain) {
                return toDtoFunction.apply(domain);
            }

            @Override
            public D toDomain(T dto) {
                return toDomainFunction.apply(dto);
            }
        };
    }

    /**
     * Create a one-way mapper (domain to DTO only).
     *
     * @param toDtoFunction function to convert domain to DTO
     * @param <D> domain type
     * @param <T> DTO type
     * @return a one-way DTO mapper
     */
    static <D, T> DTOMapper<D, T> oneWay(Function<D, T> toDtoFunction) {
        return new DTOMapper<>() {
            @Override
            public T toDto(D domain) {
                return toDtoFunction.apply(domain);
            }

            @Override
            public D toDomain(T dto) {
                throw new UnsupportedOperationException("This is a one-way mapper");
            }
        };
    }

    /**
     * Compose this mapper with another mapper.
     *
     * @param other the other mapper to compose with
     * @param <R> the target type
     * @return a composed mapper
     */
    default <R> DTOMapper<D, R> compose(DTOMapper<T, R> other) {
        return new DTOMapper<>() {
            @Override
            public R toDto(D domain) {
                return other.toDto(DTOMapper.this.toDto(domain));
            }

            @Override
            public D toDomain(R dto) {
                return DTOMapper.this.toDomain(other.toDomain(dto));
            }
        };
    }
}
