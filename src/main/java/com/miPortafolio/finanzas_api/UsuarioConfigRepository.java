package com.miPortafolio.finanzas_api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface UsuarioConfigRepository extends JpaRepository<UsuarioConfig, Long> {}

@Repository
interface PerfilFinancieroRepository extends JpaRepository<PerfilFinanciero, Long> {}

@Repository
interface MetaAhorroRepository extends JpaRepository<MetaAhorro, Long> {
    List<MetaAhorro> findByChatIdAndActiva(Long chatId, boolean activa);
    Optional<MetaAhorro> findFirstByChatIdAndMontoObjetivoIsNull(Long chatId);
}

@Repository
interface GastoRepository extends JpaRepository<Gasto, Long> {
    List<Gasto> findByChatIdAndFecha(Long chatId, LocalDate fecha);
    List<Gasto> findByChatIdAndFechaBetween(Long chatId, LocalDate desde, LocalDate hasta);

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g WHERE g.chatId = :chatId AND g.fecha = :fecha")
    Double sumMontoByChatIdAndFecha(Long chatId, LocalDate fecha);

    @Query("SELECT COALESCE(SUM(g.monto), 0) FROM Gasto g WHERE g.chatId = :chatId AND g.fecha BETWEEN :desde AND :hasta")
    Double sumMontoByChatIdAndFechaBetween(Long chatId, LocalDate desde, LocalDate hasta);
}