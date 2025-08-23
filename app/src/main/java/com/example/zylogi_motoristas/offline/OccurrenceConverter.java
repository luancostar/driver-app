package com.example.zylogi_motoristas.offline;

import com.example.zylogi_motoristas.Occurrence;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversor para transformar objetos Occurrence em OccurrenceEntity e vice-versa
 */
public class OccurrenceConverter {
    
    /**
     * Converte um objeto Occurrence em OccurrenceEntity
     */
    public static OccurrenceEntity toEntity(Occurrence occurrence) {
        if (occurrence == null) {
            return null;
        }
        
        OccurrenceEntity entity = new OccurrenceEntity();
        entity.setId(occurrence.getId());
        entity.setReferenceId(occurrence.getReferenceId());
        entity.setOccurrenceNumber(occurrence.getOccurrenceNumber());
        entity.setName(occurrence.getName());
        entity.setClientFault(occurrence.isClientFault());
        entity.setSendToApp(occurrence.isSendToApp());
        entity.setActivated(occurrence.isActivated());
        entity.setCreatedAt(occurrence.getCreatedAt());
        entity.setUpdatedAt(occurrence.getUpdatedAt());
        entity.setCachedAt(System.currentTimeMillis());
        
        return entity;
    }
    
    /**
     * Converte uma lista de Occurrence em lista de OccurrenceEntity
     */
    public static List<OccurrenceEntity> toEntityList(List<Occurrence> occurrences) {
        if (occurrences == null) {
            return new ArrayList<>();
        }
        
        List<OccurrenceEntity> entities = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            OccurrenceEntity entity = toEntity(occurrence);
            if (entity != null) {
                entities.add(entity);
            }
        }
        
        return entities;
    }
    
    /**
     * Converte um OccurrenceEntity em Occurrence
     */
    public static Occurrence fromEntity(OccurrenceEntity entity) {
        if (entity == null) {
            return null;
        }
        
        Occurrence occurrence = new Occurrence();
        occurrence.setId(entity.getId());
        occurrence.setReferenceId(entity.getReferenceId());
        occurrence.setOccurrenceNumber(entity.getOccurrenceNumber());
        occurrence.setName(entity.getName());
        occurrence.setClientFault(entity.isClientFault());
        occurrence.setSendToApp(entity.isSendToApp());
        occurrence.setActivated(entity.isActivated());
        occurrence.setCreatedAt(entity.getCreatedAt());
        occurrence.setUpdatedAt(entity.getUpdatedAt());
        
        return occurrence;
    }
    
    /**
     * Converte uma lista de OccurrenceEntity em lista de Occurrence
     */
    public static List<Occurrence> fromEntityList(List<OccurrenceEntity> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }
        
        List<Occurrence> occurrences = new ArrayList<>();
        for (OccurrenceEntity entity : entities) {
            Occurrence occurrence = fromEntity(entity);
            if (occurrence != null) {
                occurrences.add(occurrence);
            }
        }
        
        return occurrences;
    }
}