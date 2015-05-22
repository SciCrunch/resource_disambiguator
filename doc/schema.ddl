
    create table rd_acr_paper_path (
        id  bigserial not null,
        file_path varchar(255) not null,
        pubmed_id varchar(20),
        primary key (id)
    );

    create table rd_acronym (
        id  bigserial not null,
        acronym varchar(100) not null,
        frequency int4 not null,
        primary key (id)
    );

    create table rd_acronym_expansion (
        id  bigserial not null,
        cluster_id int4,
        expansion varchar(255) not null,
        frequency int4 not null,
        acr_id int8,
        primary key (id)
    );

    create table rd_checkpoint (
        id  bigserial not null,
        batch_id varchar(10) not null,
        mod_time timestamp not null,
        op_type varchar(50) not null,
        pk_value int8 not null,
        status varchar(50) not null,
        table_name varchar(50) not null,
        primary key (id)
    );

    create table rd_comb_resource_ref (
        id  bigserial not null,
        confidence float8,
        nif_id varchar(30) not null,
        pubmed_id varchar(255) not null,
        registry_id int4 not null,
        src varchar(10) not null,
        primary key (id)
    );

    create table rd_down_site_status (
        id  bigserial not null,
        batch_id varchar(8),
        label varchar(5),
        last_checked_time timestamp,
        message varchar(255),
        mod_time timestamp,
        modified_by varchar(40),
        nif_id varchar(50),
        num_of_consecutive_checks int4,
        resource_name varchar(255),
        url varchar(255),
        primary key (id)
    );

    create table rd_job_log (
        id  bigserial not null,
        batch_id varchar(6) not null,
        mod_time timestamp,
        modified_by varchar(40),
        operation varchar(40) not null,
        status varchar(30) not null,
        primary key (id)
    );

    create table rd_ner_annot_info (
        id  bigserial not null,
        label varchar(5),
        mod_time timestamp,
        modified_by varchar(40),
        notes varchar(1024),
        op_type varchar(30),
        registry_id int8,
        rr_id int8,
        primary key (id)
    );

    create table rd_ner_paper_path (
        id  bigserial not null,
        file_path varchar(255) not null,
        flags int4,
        primary key (id)
    );

    create table rd_paper (
        id  bigserial not null,
        doi varchar(255),
        file_path varchar(255),
        journal_title varchar(1000),
        pmc_id varchar(20),
        pubdate varchar(10),
        pubmed_id varchar(255),
        title varchar(2000),
        primary key (id)
    );

    create table rd_paper_acronyms (
        id  bigserial not null,
        acronym varchar(100),
        expansion varchar(255),
        pubmed_id varchar(20),
        primary key (id)
    );

    create table rd_paper_path (
        id  bigserial not null,
        file_path varchar(255) not null,
        flags int4,
        primary key (id)
    );

    create table rd_paper_reference (
        id  bigserial not null,
        authors varchar(1024),
        c_score float8,
        description text,
        flags int4 not null,
        genre varchar(40),
        mesh_headings varchar(1024),
        publication_date varchar(40),
        publication_name varchar(1000),
        publisher_doc_id varchar(100),
        pubmed_id varchar(255),
        title varchar(1000),
        publisher_id int8,
        query_log_id int8,
        registry_id int8,
        primary key (id)
    );

    create table rd_parsed_paper_sentence (
        id  bigserial not null,
        pt varchar(1024) not null,
        pubmed_id varchar(20) not null,
        sentence varchar(500) not null,
        primary key (id)
    );

    create table rd_ps_annot_info (
        id  bigserial not null,
        label varchar(5),
        mod_time timestamp,
        modified_by varchar(40),
        notes varchar(1024),
        op_type varchar(30),
        pr_id int8,
        registry_id int8,
        primary key (id)
    );

    create table rd_publisher (
        id  bigserial not null,
        api_key varchar(255),
        connections_allowed int4,
        publisher_name varchar(255),
        primary key (id)
    );

    create table rd_publisher_query_log (
        id  bigserial not null,
        exec_time timestamp,
        query_str varchar(1000),
        publisher_id int8,
        registry_id int8,
        primary key (id)
    );

    create table rd_redirect_history (
        id  bigserial not null,
        mod_time timestamp,
        modified_by varchar(40),
        redirect_url varchar(255),
        registry_id int8,
        primary key (id)
    );

    create table rd_redirect_url (
        id  bigserial not null,
        redirect_url varchar(255) not null,
        url_id int8,
        primary key (id)
    );

    create table rd_reg_redirect_annot_info (
        id  bigserial not null,
        c_score float8 not null,
        label varchar(5),
        mod_time timestamp,
        modified_by varchar(40),
        notes varchar(1024),
        redirect_url varchar(255),
        status int4 not null,
        registry_id int8,
        primary key (id)
    );

    create table rd_reg_update_status (
        id  bigserial not null,
        batch_id varchar(8),
        containment float8,
        cos_similarity float8,
        last_checked_time timestamp,
        sem_similarity float8,
        similarity float8,
        update_line varchar(1000),
        update_year varchar(4),
        registry_id int8,
        primary key (id)
    );

    create table rd_registry_site_content (
        id  bigserial not null,
        content text,
        flags int4 not null,
        last_mod_time timestamp,
        redirect_url varchar(500),
        title varchar(1024),
        registry_id int8,
        primary key (id)
    );

    create table rd_resource_candidate (
        id  bigserial not null,
        batch_id varchar(6),
        description varchar(2048),
        mod_time timestamp,
        resource_type varchar(255),
        score float8,
        status varchar(20),
        title varchar(512),
        url_id int8,
        primary key (id)
    );

    create table rd_resource_ref (
        id  bigserial not null,
        c_score float8,
        context varchar(2048),
        end_idx int4,
        entity varchar(1000),
        flags int4 not null,
        start_idx int4,
        doc_id int8,
        registry_id int8,
        primary key (id)
    );

    create table rd_resource_status (
        id  bigserial not null,
        last_checked_time timestamp,
        is_valid bool,
        doc_id int8,
        registry_id int8,
        primary key (id)
    );

    create table rd_url_annot_info (
        id  bigserial not null,
        label varchar(5),
        mod_time timestamp,
        modified_by varchar(40),
        notes varchar(1024),
        op_type varchar(30),
        resource_type varchar(255),
        registry_id int8,
        url_id int8,
        primary key (id)
    );

    create table rd_url_status (
        id  bigserial not null,
        alive bool,
        flags int4 not null,
        last_mod_time timestamp,
        type int4 not null,
        url_id int8,
        primary key (id)
    );

    create table rd_urls (
        id  bigserial not null,
        batch_id varchar(6),
        c_score float8,
        context varchar(2048),
        description varchar(2048),
        flags int4 not null,
        host_link_size int4,
        line_number_in_file int4,
        rank int4,
        resource_type varchar(255),
        resource_type_src int4,
        score float8,
        update_info varchar(2048),
        url varchar(255) not null,
        doc_id int8,
        registry_id int8,
        primary key (id)
    );

    create table rd_user (
        id  bigserial not null,
        date_created timestamp,
        email varchar(20),
        login_id varchar(20) not null unique,
        password varchar(20),
        role varchar(20),
        primary key (id)
    );

    create table rd_user_registry (
        user_id int8 not null,
        registry_id int8 not null,
        primary key (user_id, registry_id),
        unique (registry_id)
    );

    create table rd_validation_status (
        id  bigserial not null,
        last_checked_time timestamp,
        message varchar(255),
        is_up bool,
        registry_id int8,
        primary key (id)
    );

    create table registry (
        id  bigserial not null,
        abbrev text,
        alt_url varchar(500),
        availability text,
        comment text,
        curation_status text,
        date_created int4,
        date_updated int4,
        description text,
        grants text,
        index_time int4,
        keyword text,
        license_text text,
        license_url text,
        logo text,
        nif_id text,
        nif_pmid_link text,
        old_url varchar(500),
        parent_organization text,
        parent_organization_id text,
        publicationlink text,
        relatedto text,
        resource_name text,
        resource_type text,
        resource_type_ids text,
        resource_updated int4,
        supercategory varchar(200),
        supporting_agency text,
        supporting_agency_id text,
        synonym text,
        url text,
        uuid text,
        primary key (id)
    );

    alter table rd_acronym_expansion 
        add constraint FK5A5E10765F8CECAB 
        foreign key (acr_id) 
        references rd_acronym;

    alter table rd_ner_annot_info 
        add constraint FKD932F09895ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_ner_annot_info 
        add constraint FKD932F098A70B1DA 
        foreign key (rr_id) 
        references rd_resource_ref;

    alter table rd_paper_reference 
        add constraint FKC5D78C4B95ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_paper_reference 
        add constraint FKC5D78C4B52001478 
        foreign key (publisher_id) 
        references rd_publisher;

    alter table rd_paper_reference 
        add constraint FKC5D78C4B15B5A903 
        foreign key (query_log_id) 
        references rd_publisher_query_log;

    alter table rd_ps_annot_info 
        add constraint FK1025663695ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_ps_annot_info 
        add constraint FK10256636D8831A19 
        foreign key (pr_id) 
        references rd_paper_reference;

    alter table rd_publisher_query_log 
        add constraint FK6D7F273D95ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_publisher_query_log 
        add constraint FK6D7F273D52001478 
        foreign key (publisher_id) 
        references rd_publisher;

    alter table rd_redirect_history 
        add constraint FKD95A259E95ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_redirect_url 
        add constraint FKF9AA1B79FD533B0E 
        foreign key (url_id) 
        references rd_urls;

    alter table rd_reg_redirect_annot_info 
        add constraint FK5B53AD295ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_reg_update_status 
        add constraint FK16456F095ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_registry_site_content 
        add constraint FKB4C485F695ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_resource_candidate 
        add constraint FKFE6A71BFFD533B0E 
        foreign key (url_id) 
        references rd_urls;

    alter table rd_resource_ref 
        add constraint FKAA1371CF95ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_resource_ref 
        add constraint FKAA1371CF25D2D64C 
        foreign key (doc_id) 
        references rd_paper;

    alter table rd_resource_status 
        add constraint FKEF50BD3695ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_resource_status 
        add constraint FKEF50BD3625D2D64C 
        foreign key (doc_id) 
        references rd_paper;

    alter table rd_url_annot_info 
        add constraint FKF4C0E84495ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_url_annot_info 
        add constraint FKF4C0E844FD533B0E 
        foreign key (url_id) 
        references rd_urls;

    alter table rd_urls 
        add constraint FK3E9CA2D195ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_urls 
        add constraint FK3E9CA2D125D2D64C 
        foreign key (doc_id) 
        references rd_paper;

    alter table rd_user_registry 
        add constraint FK37D7AAE495ACF89C 
        foreign key (registry_id) 
        references registry;

    alter table rd_user_registry 
        add constraint FK37D7AAE497E9F6DC 
        foreign key (user_id) 
        references rd_user;

    alter table rd_validation_status 
        add constraint FKB9C649AB95ACF89C 
        foreign key (registry_id) 
        references registry;
