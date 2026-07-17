package com.cookplanner.repository;

import com.cookplanner.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {
    Optional<Ingredient> findByName(String name);

    Optional<Ingredient> findByNameIgnoreCase(String name);

    List<Ingredient> findByNameContainingIgnoreCase(String name);

    List<Ingredient> findAllByNameInIgnoreCase(Collection<String> names);
}
