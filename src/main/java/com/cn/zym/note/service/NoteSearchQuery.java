package com.cn.zym.note.service;

import com.cn.zym.note.entity.NoteEntity;
import com.cn.zym.note.entity.NoteTagEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoteSearchQuery {

    private final EntityManager em;

    public Page<NoteEntity> pageActiveNotesWithKeyword(
            long userId,
            String keyword,
            Long notebookFilterId,
            Long tagFilterId,
            Boolean favoriteOnly,
            Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<NoteEntity> cq = cb.createQuery(NoteEntity.class);
        Root<NoteEntity> n = cq.from(NoteEntity.class);
        cq.distinct(true);
        cq.select(n);

        List<Predicate> ps = new ArrayList<>();
        ps.add(cb.equal(n.get("user").get("id"), userId));
        ps.add(cb.isNull(n.get("deletedAt")));
        if (Boolean.TRUE.equals(favoriteOnly)) {
            ps.add(cb.isTrue(n.get("favored")));
        }
        if (notebookFilterId != null) {
            ps.add(cb.equal(n.get("notebook").get("id"), notebookFilterId));
        }
        if (tagFilterId != null) {
            Subquery<Long> hasTag = cq.subquery(Long.class);
            Root<NoteTagEntity> nt0 = hasTag.from(NoteTagEntity.class);
            hasTag.select(nt0.get("note").get("id"))
                    .where(cb.and(cb.equal(nt0.get("note").get("id"), n.get("id")), cb.equal(
                            nt0.get("tag").get("id"), tagFilterId)));
            ps.add(cb.exists(hasTag));
        }

        String like = "%" + keyword.trim().toLowerCase() + "%";
        Predicate title = cb.like(cb.lower(n.get("title")), like);
        Predicate preview = cb.like(cb.lower(n.get("preview")), like);
        Predicate body = cb.like(cb.lower(n.get("contentHtml")), like);

        Subquery<Long> tagNameMatch = cq.subquery(Long.class);
        Root<NoteTagEntity> nt = tagNameMatch.from(NoteTagEntity.class);
        Join<?, ?> tag = nt.join("tag", JoinType.INNER);
        tagNameMatch
                .select(cb.literal(1L))
                .where(cb.and(
                        cb.equal(nt.get("note").get("id"), n.get("id")),
                        cb.like(cb.lower(tag.get("name")), like)));

        ps.add(cb.or(title, preview, body, cb.exists(tagNameMatch)));
        cq.where(ps.toArray(Predicate[]::new));
        cq.orderBy(cb.desc(n.get("updatedAt")));

        TypedQuery<NoteEntity> tq = em.createQuery(cq);
        tq.setFirstResult((int) pageable.getOffset());
        tq.setMaxResults(pageable.getPageSize());
        List<NoteEntity> list = tq.getResultList();

        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<NoteEntity> cn = countQ.from(NoteEntity.class);
        List<Predicate> cps = new ArrayList<>();
        cps.add(cb.equal(cn.get("user").get("id"), userId));
        cps.add(cb.isNull(cn.get("deletedAt")));
        if (Boolean.TRUE.equals(favoriteOnly)) {
            cps.add(cb.isTrue(cn.get("favored")));
        }
        if (notebookFilterId != null) {
            cps.add(cb.equal(cn.get("notebook").get("id"), notebookFilterId));
        }
        if (tagFilterId != null) {
            Subquery<Long> tg = countQ.subquery(Long.class);
            Root<NoteTagEntity> mtx = tg.from(NoteTagEntity.class);
            tg.select(mtx.get("note").get("id"))
                    .where(cb.and(cb.equal(mtx.get("note").get("id"), cn.get("id")), cb.equal(
                            mtx.get("tag").get("id"), tagFilterId)));
            cps.add(cb.exists(tg));
        }

        Predicate t2 = cb.like(cb.lower(cn.get("title")), like);
        Predicate p2 = cb.like(cb.lower(cn.get("preview")), like);
        Predicate b2 = cb.like(cb.lower(cn.get("contentHtml")), like);

        Subquery<Long> tg2 = countQ.subquery(Long.class);
        Root<NoteTagEntity> ntx = tg2.from(NoteTagEntity.class);
        Join<?, ?> tg = ntx.join("tag", JoinType.INNER);
        tg2.select(cb.literal(1L))
                .where(cb.and(
                        cb.equal(ntx.get("note").get("id"), cn.get("id")),
                        cb.like(cb.lower(tg.get("name")), like)));

        cps.add(cb.or(t2, p2, b2, cb.exists(tg2)));

        countQ.select(cb.countDistinct(cn)).where(cps.toArray(Predicate[]::new));
        long total = em.createQuery(countQ).getSingleResult();

        return new PageImpl<>(list, pageable, total);
    }
}
