package streamsend.pillar

import java.util.Date
import com.datastax.driver.core.Session
import com.datastax.driver.core.querybuilder.QueryBuilder


object Migration {
  def apply(description: String, authoredAt: Date, up: String): Migration = {
    new IrreversibleMigration(description, authoredAt, up)
  }

  def apply(description: String, authoredAt: Date, up: String, down: Option[String]): Migration = {
    down match {
      case Some(downStatement) =>
        new ReversibleMigration(description, authoredAt, up, downStatement)
      case None =>
        new ReversibleMigrationWithNoOpDown(description, authoredAt, up)
    }
  }
}

abstract class Migration(val description: String, val authoredAt: Date, protected val up: String) {
  def key: MigrationKey = MigrationKey(authoredAt, description)

  def authoredAfter(date: Date): Boolean = {
    authoredAt.after(date)
  }

  def authoredBefore(date: Date): Boolean = {
    authoredAt.compareTo(date) <= 0
  }

  def executeUpStatement(session: Session) {
    session.execute(up)
    insertIntoAppliedMigrations(session)
  }

  def executeDownStatement(session: Session)

  protected def deleteFromAppliedMigrations(session: Session) {
    session.execute(QueryBuilder.
      delete().
      from("applied_migrations").
      where(QueryBuilder.eq("authored_at", authoredAt)).
      and(QueryBuilder.eq("description", description))
    )
  }

  private def insertIntoAppliedMigrations(session: Session) {
    session.execute(QueryBuilder.
      insertInto("applied_migrations").
      value("authored_at", authoredAt).
      value("description", description).
      value("applied_at", System.currentTimeMillis())
    )
  }
}

class IrreversibleMigration(description: String, authoredAt: Date, up: String) extends Migration(description, authoredAt, up) {
  def executeDownStatement(session: Session) {
    throw new IrreversibleMigrationException(this)
  }
}

class ReversibleMigrationWithNoOpDown(description: String, authoredAt: Date, up: String) extends Migration(description, authoredAt, up) {
  def executeDownStatement(session: Session) {
    deleteFromAppliedMigrations(session)
  }
}

class ReversibleMigration(description: String, authoredAt: Date, up: String, down: String) extends Migration(description, authoredAt, up) {
  def executeDownStatement(session: Session) {
    session.execute(down)
    deleteFromAppliedMigrations(session)
  }
}