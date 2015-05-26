create index us_url_id_idx on rd_url_status(url_id);
create index us_type_idx on rd_url_status(type);
create index us_alive_idx on rd_url_status(alive);

create index crr_nif_id_idx on rd_comb_resource_ref(nif_id);
create index pr_flags_idx on rd_paper_reference(flags);
create index registry_nif_id_idx on registry(nif_id);
create index urls_doc_id_idx on rd_urls(doc_id);
create index rr_doc_id_idx on rd_resource_ref(doc_id);
create index pp_file_path_idx on rd_paper_path(file_path);
create index pr_pmid_idx on rd_paper_reference(pubmed_id);

create index paper_pubdate_idx on rd_paper(pubdate);
create index urls_resource_type_idx on rd_urls(resource_type);

create index pr_pdoc_id_idx on rd_paper_reference(publisher_doc_id);

create index npp_file_path_idx on rd_ner_paper_path(file_path);

create index pa_pmid_idx on rd_paper_acronyms(pubmed_id);
create index app_file_path_idx on rd_acr_paper_path(file_path);
create index app_pmid_idx on rd_acr_paper_path(pubmed_id);

create index pa_acr_idx on rd_paper_acronyms(acronym);

create index acr_acr_idx on rd_acronym(acronym);
create index acr_freq_idx on rd_acronym(frequency);

create index ae_acr_idx on rd_acronym_expansion(acr_id);
