# Fetch EIP by tag
data "aws_eip" "db" {
  filter {
    name   = "tag:Name"
    values = ["MYSQL"]
  }
}

data "aws_eip" "application" {
  filter {
    name   = "tag:Name"
    values = ["Application"]
  }
}
