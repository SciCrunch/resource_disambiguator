Resource Disambiguator Batch Processes
======================================

Prerequisites
-------------
 * Java 1.6+
 * Maven 3+
 * Postgres 8+

Getting the code
----------------

    cd $HOME
    git clone https://github.com/SciCrunch/resource_disambiguator.git
    cd $HOME/resource_disambiguator

Database
--------

First create a Postgres database named rd_prod with a user named 'rd_prod' 

    su - postgres
    createdb --encoding='utf-8' --locale=en_US.utf8 --template=template0 rd_prod
    psql rd_prod
    create user rd_prod with password '<your-password>';
    grant all privileges on database rd_prod to rd_prod;
   
Then exit postgres account and apply the schema and indices to the newly 
created database.
   
    cd $HOME/resource_disambiguator/doc
    psql rd_prod -U rd_prod
    \i schema.ddl
    \i indices.sql
    \q


Building
--------

First install dependencies to your local maven repository. This is a one time thing.

   cd $HOME/resource_disambiguator/dependencies
   ./install_bnlp_2mvn.sh
   ./install_bnlp_model2mvn.sh
   ./install_bnlp_dependencies_2mvn.sh
   ./install_other_dependencies_2mvn.sh

Then 


