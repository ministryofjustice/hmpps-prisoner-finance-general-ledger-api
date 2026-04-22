delete from transactions as t where t.transaction_id   in (
    select distinct t.transaction_id   from postings p
                                                join transactions t
                                                     on p.transaction_id = t.transaction_id
                                                join sub_accounts sa
                                                     on p.sub_account_id  = sa.sub_account_id
                                                join accounts a
                                                     on a.account_id = sa.account_id
    where a.reference like '%TEST%'
)

delete from postings as p where p.posting_id in (
    select p.posting_id  from postings p
                                  join sub_accounts sa
                                       on p.sub_account_id  = sa.sub_account_id
                                  join accounts a
                                       on a.account_id = sa.account_id
    where a.reference like '%TEST%'
)